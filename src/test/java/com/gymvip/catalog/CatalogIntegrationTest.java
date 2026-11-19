package com.gymvip.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.gymvip.catalog.CatalogDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Import(CatalogIntegrationTest.TimeConfig.class)
@WithMockUser(username="admin@vipcenterfit.pe",roles="ADMIN")
class CatalogIntegrationTest {
    @Autowired CatalogService catalog;
    @Autowired CatalogImageValidator images;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    static final String ACTOR="admin@vipcenterfit.pe";
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-02T17:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock catalogClock(){return new MutableClock();}}
    @BeforeEach void resetClock(){clock.now=Instant.parse("2026-11-02T17:00:00Z");}
    MockMultipartFile photo(String name) throws IOException {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("images",name,"image/png",out.toByteArray());
    }
    SupplementInput input(String name,BigDecimal price,Long version,List<UUID> keep,String main) {
        return new SupplementInput(name,"Marca test","PROTEINA","Pote","900 g","Vainilla","Información del rotulado","Consultar envase",price,"DISPONIBLE",true,true,version,keep,main);
    }
    Supplement create() throws IOException {return catalog.saveSupplement(null,input("Producto "+UUID.randomUUID(),new BigDecimal("150.00"),null,List.of(),"upload:0"),List.of(photo("frontal.png")),ACTOR);}
    PromotionInput offer(Supplement p,BigDecimal amount,LocalDate start,LocalDate end,Long version) {
        return new PromotionInput(p.id(),"Oferta "+UUID.randomUUID(),"Descuento de prueba","No acumulable",amount,start,end,true,true,version,p.version());
    }
    Promotion active(Supplement p){return catalog.savePromotion(null,offer(p,new BigDecimal("135.00"),LocalDate.of(2026,11,1),LocalDate.of(2026,11,15),null),ACTOR);}
    @Test void createWithRealImageStoresHistoryAndSanitizesFilename() throws Exception {
        var body=input("HTTP "+UUID.randomUUID(),new BigDecimal("150.00"),null,List.of(),"upload:0");
        String response=mvc.perform(multipart("/api/v1/catalog/supplements").file(new MockMultipartFile("data","data.json","application/json",json.writeValueAsBytes(body))).file(photo("../frontal.png")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.images[0].filename").value("frontal.png")).andReturn().getResponse().getContentAsString();
        UUID id=UUID.fromString(json.readTree(response).get("id").asText());assertEquals(1,catalog.supplementHistory(id,0).size());
        var image=catalog.supplement(id,false).images().getFirst();assertEquals("image/png",catalog.image(id,image.id(),false,false).mediaType());
    }
    @Test void requiredFieldsAndPriceAreValidatedBeforePersistence() throws Exception {
        long count=jdbc.queryForObject("SELECT count(*) FROM supplements",Long.class);
        mvc.perform(multipart("/api/v1/catalog/supplements").file(new MockMultipartFile("data","data.json","application/json",json.writeValueAsBytes(input("",BigDecimal.ZERO,null,List.of(),null)))).file(photo("f.png"))).andExpect(status().isBadRequest());
        assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM supplements",Long.class));
    }
    @Test void imageFormatSizeAndMissingPhotoAreValidated() throws Exception {
        assertThrows(IllegalArgumentException.class,()->images.read(new MockMultipartFile("images","fake.png","image/png","<script>bad</script>".getBytes())));
        assertThrows(IllegalArgumentException.class,()->images.read(new MockMultipartFile("images","big.png","image/png",new byte[5*1024*1024+1])));
        assertThrows(IllegalArgumentException.class,()->catalog.saveSupplement(null,input("Sin foto",BigDecimal.TEN,null,List.of(),null),List.of(),ACTOR));
    }
    @Test void updateKeepsOriginalPriceHistoryAndSupportsNewPrincipalImage() throws Exception {
        var first=create();var updated=catalog.saveSupplement(first.id(),input(first.name(),new BigDecimal("180.00"),first.version(),List.of(first.images().getFirst().id()),"upload:0"),List.of(photo("nueva.png")),ACTOR);
        assertEquals("nueva.png",updated.images().getFirst().filename());assertEquals(2,updated.images().size());
        assertTrue(catalog.supplementHistory(first.id(),0).get(1).snapshot().contains("150.00"));
        assertThrows(ConflictException.class,()->catalog.saveSupplement(first.id(),input(first.name(),BigDecimal.TEN,first.version(),List.of(first.images().getFirst().id()),null),List.of(),ACTOR));
    }
    @Test void foreignImageRollsBackMetadataAndImageChanges() throws Exception {
        var p=create();assertThrows(IllegalArgumentException.class,()->catalog.saveSupplement(p.id(),input(p.name(),new BigDecimal("180.00"),p.version(),List.of(UUID.randomUUID()),null),List.of(),ACTOR));
        assertEquals(p.version(),catalog.supplement(p.id(),false).version());assertEquals(new BigDecimal("150.00"),catalog.supplement(p.id(),false).pricePen());
    }
    @Test void hiddenProductsRemainStoredAndSoldOutProductsStayPublished() throws Exception {
        var p=create();var sold=catalog.state(p.id(),new StateInput(p.version(),"AGOTADO",true),ACTOR);assertEquals("AGOTADO",catalog.supplement(p.id(),true).availability());
        catalog.state(p.id(),new StateInput(sold.version(),"AGOTADO",false),ACTOR);assertThrows(ResourceNotFoundException.class,()->catalog.supplement(p.id(),true));assertFalse(catalog.supplement(p.id(),false).published());
    }
    @Test void fixedDiscountProduces165AfterPriceChangesFrom150To180() throws Exception {
        var p=create();var promotion=active(p);var changed=catalog.saveSupplement(p.id(),input(p.name(),new BigDecimal("180.00"),p.version(),List.of(p.images().getFirst().id()),null),List.of(),ACTOR);
        catalog.state(p.id(),new StateInput(changed.version(),"AGOTADO",true),ACTOR);var current=catalog.promotion(promotion.id(),true);
        assertEquals(new BigDecimal("165.00"),current.offerPricePen());assertEquals(new BigDecimal("15.00"),current.savingsPen());assertEquals("AGOTADO",current.supplement().availability());assertTrue(catalog.promotionHistory(promotion.id(),0).getFirst().snapshot().contains("135.00"));
    }
    @Test void catalogPriceCannotInvalidateAnEnabledDiscount() throws Exception {
        var p=create();active(p);assertThrows(ConflictException.class,()->catalog.saveSupplement(p.id(),input(p.name(),new BigDecimal("15.00"),p.version(),List.of(p.images().getFirst().id()),null),List.of(),ACTOR));assertEquals(new BigDecimal("150.00"),catalog.supplement(p.id(),false).pricePen());
    }
    @Test void limaDatesHaveInclusiveLastDayAndDisablePreservesHistory() throws Exception {
        var p=create();var promotion=catalog.savePromotion(null,offer(p,new BigDecimal("135.00"),LocalDate.of(2026,11,3),LocalDate.of(2026,11,10),null),ACTOR);
        assertEquals("PROGRAMADA",promotion.state());assertThrows(ResourceNotFoundException.class,()->catalog.promotion(promotion.id(),true));
        clock.now=Instant.parse("2026-11-03T05:00:00Z");assertEquals("VIGENTE",catalog.promotion(promotion.id(),true).state());
        clock.now=Instant.parse("2026-11-11T04:59:59Z");assertEquals("VIGENTE",catalog.promotion(promotion.id(),true).state());
        clock.now=Instant.parse("2026-11-11T05:00:00Z");assertEquals("FINALIZADA",catalog.promotion(promotion.id(),false).state());assertThrows(ResourceNotFoundException.class,()->catalog.promotion(promotion.id(),true));
        assertEquals("CANCELADA",catalog.deactivatePromotion(promotion.id(),new PromotionCancellation(promotion.version()),ACTOR).state());assertEquals(2,catalog.promotionHistory(promotion.id(),0).size());
    }
    @Test void invalidDatesPricesAndOverlappingOffersAreRejected() throws Exception {
        var p=create();active(p);assertThrows(ConflictException.class,()->active(p));
        assertThrows(IllegalArgumentException.class,()->catalog.savePromotion(null,offer(p,new BigDecimal("150.00"),LocalDate.of(2026,12,1),LocalDate.of(2026,12,10),null),ACTOR));
        assertThrows(IllegalArgumentException.class,()->catalog.savePromotion(null,offer(p,BigDecimal.TEN,LocalDate.of(2026,12,10),LocalDate.of(2026,12,1),null),ACTOR));
    }
    @Test void simultaneousOverlappingPromotionsHaveOnlyOneWinner() throws Exception {
        var p=create();var request=offer(p,new BigDecimal("135.00"),LocalDate.of(2026,12,1),LocalDate.of(2026,12,10),null);
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);Callable<String> task=()->{gate.await();try{catalog.savePromotion(null,request,ACTOR);return "saved";}catch(ConflictException ex){return "conflict";}};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();assertEquals(Set.of("saved","conflict"),Set.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
        }
    }
    @Test void staleProductAndPromotionVersionsAreRejected() throws Exception {
        var p=create();var request=offer(p,new BigDecimal("135.00"),LocalDate.of(2026,11,1),LocalDate.of(2026,11,15),null);
        catalog.state(p.id(),new StateInput(p.version(),"AGOTADO",true),ACTOR);assertThrows(ConflictException.class,()->catalog.savePromotion(null,request,ACTOR));
        var current=catalog.supplement(p.id(),false);var promotion=active(current);catalog.deactivatePromotion(promotion.id(),new PromotionCancellation(promotion.version()),ACTOR);
        assertThrows(ConflictException.class,()->catalog.savePromotion(promotion.id(),offer(current,new BigDecimal("135.00"),promotion.startDate(),promotion.endDate(),promotion.version()),ACTOR));
    }
    @Test @WithMockUser(username="recepcion@vipcenterfit.pe",roles="RECEPCION")
    void receptionCanReadButCannotWrite() throws Exception {
        var p=create();mvc.perform(get("/api/v1/catalog/supplements")).andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/catalog/supplements").file(new MockMultipartFile("data","data.json","application/json",json.writeValueAsBytes(input("No permitido",BigDecimal.TEN,null,List.of(),null)))).file(photo("f.png"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/catalog/supplements/"+p.id()+"/state").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new StateInput(p.version(),"AGOTADO",true)))).andExpect(status().isForbidden());
    }
    @Test void memberReadsCurrentPublishedCatalogWithoutMembership() throws Exception {
        var published = create();
        var hidden = create();
        catalog.state(hidden.id(), new StateInput(hidden.version(), "AGOTADO", false), ACTOR);
        var updated = catalog.saveSupplement(published.id(),
                input(published.name(), new BigDecimal("180.00"), published.version(),
                        List.of(published.images().getFirst().id()), null), List.of(), ACTOR);
        var offer = catalog.savePromotion(null, offer(updated, new BigDecimal("165.00"),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), null), ACTOR);
        catalog.state(updated.id(), new StateInput(updated.version(), "AGOTADO", true), ACTOR);

        var member = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("socio").roles("CLIENTE");
        mvc.perform(get("/api/v1/catalog/supplements/{id}", published.id()).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePen").value(180))
                .andExpect(jsonPath("$.availability").value("AGOTADO"));
        String item = "$[?(@.id == '" + published.id() + "')]";
        mvc.perform(get("/api/v1/catalog/products").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".brand").value(org.hamcrest.Matchers.contains("Marca test")))
                .andExpect(jsonPath(item + ".availability").value(org.hamcrest.Matchers.contains("AGOTADO")))
                .andExpect(jsonPath(item + ".offer.id").value(org.hamcrest.Matchers.contains(offer.id().toString())))
                .andExpect(jsonPath("$[?(@.id == '" + hidden.id() + "')]").isEmpty());
        String photoUrl = "/api/v1/catalog/supplements/" + published.id() + "/images/"
                + updated.images().getFirst().id() + "?thumbnail=true";
        mvc.perform(get(photoUrl).with(member)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(patch("/api/v1/catalog/supplements/{id}/state", published.id()).with(member)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":1,"availability":"DISPONIBLE","published":true}
                                """))
                .andExpect(status().isForbidden());
        var futureProduct = create();
        var future = catalog.savePromotion(null, offer(futureProduct,
                new BigDecimal("140.00"), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25), null), ACTOR);
        var disabledProduct = create();
        var disabled = active(disabledProduct);
        catalog.deactivatePromotion(disabled.id(), new PromotionCancellation(disabled.version()), ACTOR);
        clock.now = Instant.parse("2026-11-16T05:00:00Z");
        mvc.perform(get("/api/v1/catalog/products").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".isPromotion").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath(item + ".finishedOffer.id").value(org.hamcrest.Matchers.contains(offer.id().toString())))
                .andExpect(jsonPath(item + ".finishedOffer.pricePen").value(org.hamcrest.Matchers.contains(165.0)))
                .andExpect(jsonPath("$[?(@.offer.id == '" + future.id() + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.finishedOffer.id == '" + disabled.id() + "')]").isEmpty());
        mvc.perform(get("/api/v1/catalog/supplements/{id}", hidden.id()).with(member))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/supplements/{id}/history", published.id()).with(member))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/catalog/supplements").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
