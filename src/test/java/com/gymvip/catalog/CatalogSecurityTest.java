package com.gymvip.catalog;

import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.SecurityConfig;
import com.gymvip.security.TokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.gymvip.catalog.CatalogDtos.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cadena HTTP y anotaciones reales, sin sustituir la seguridad ni requerir PostgreSQL. */
@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, TokenAuthenticationFilter.class})
class CatalogSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogService service;
    @MockitoBean SessionRepository sessions;
    @MockitoBean AccountRepository accounts;
    @MockitoBean JwtService jwt;

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID IMAGE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String BASE = "/api/v1/catalog";
    private Supplement product() {
        return new Supplement(ID, "Proteína", "Marca", "PROTEINA", "Bolsa", "1 kg", null, "Descripción", null,
                new BigDecimal("100.00"), "DISPONIBLE", true, 0, LocalDateTime.of(2026, 11, 9, 12, 0), List.of());
    }
    private void reads() {
        when(service.supplements(anyString(), anyString(), anyString(), nullable(Boolean.class), nullable(BigDecimal.class),
                nullable(BigDecimal.class), anyBoolean(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new Page<>(List.of(product()), 1, 0, 1));
        when(service.supplement(eq(ID), anyBoolean())).thenReturn(product());
        when(service.image(eq(ID), eq(IMAGE), anyBoolean(), anyBoolean()))
                .thenReturn(new BinaryImage(new byte[]{1, 2, 3}, "image/png", "hash"));
        var offer = new Promotion(ID, product(), "Oferta", "Descripción", "Condiciones", new BigDecimal("100.00"),
                new BigDecimal("90.00"), BigDecimal.TEN, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                "VIGENTE", true, 0, LocalDateTime.of(2026, 11, 9, 12, 0));
        when(service.promotions(anyString(), nullable(UUID.class), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new Page<>(List.of(offer), 1, 0, 1));
        when(service.promotion(eq(ID), anyBoolean())).thenReturn(offer);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "RECEPCION", "CLIENTE"})
    void rolesKeepReadAccessWithCustomerFiltering(String role) throws Exception {
        reads();
        var user = user("lector").roles(role);
        mvc.perform(get(BASE + "/options").with(user)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/supplements").param("published", "false").with(user)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/supplements/" + ID).with(user)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/supplements/" + ID + "/images/" + IMAGE).with(user)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/promotions").with(user)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/promotions/" + ID).with(user)).andExpect(status().isOk());
        boolean customer = role.equals("CLIENTE");
        verify(service).supplements("", "", "", customer, null, null, false, 0, 12, customer);
        verify(service).supplement(ID, customer);
        verify(service).image(ID, IMAGE, true, customer);
        verify(service).promotions("VIGENTE", null, 0, 12, customer);
        verify(service).promotion(ID, customer);
    }

    @Test void anonymousCannotReadCatalog() throws Exception {
        for (var path : List.of("/options", "/supplements", "/supplements/" + ID, "/supplements/" + ID + "/images/" + IMAGE,
                "/promotions", "/promotions/" + ID))
            mvc.perform(get(BASE + path).with(anonymous())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "RECEPCION"})
    void staffMayOmitPublishedFilterWithoutUnboxingNull(String role) throws Exception {
        reads();
        mvc.perform(get(BASE + "/supplements").with(user("personal").roles(role))).andExpect(status().isOk());
        verify(service).supplements("", "", "", null, null, null, false, 0, 12, false);
    }

    @ParameterizedTest @ValueSource(strings = {"CLIENTE", "RECEPCION"})
    void writesAndAdministrativeHistoryStayForbidden(String role) throws Exception {
        var user = user("lector").roles(role);
        for (var path : List.of("/supplements/" + ID + "/history", "/promotions/" + ID + "/history"))
            mvc.perform(get(BASE + path).with(user)).andExpect(status().isForbidden());
        var data = new MockMultipartFile("data", "data.json", "application/json", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(BASE + "/supplements").file(data).with(user)).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, BASE + "/supplements/" + ID).file(data).with(user)).andExpect(status().isForbidden());
        String offer = """
                {"supplementId":"%s","name":"Oferta","description":"Descripción","conditions":"Condiciones",
                "offerPricePen":90,"startDate":"2026-11-01","endDate":"2026-11-30","enabled":true,"reviewed":true,"supplementVersion":0}
                """.formatted(ID);
        mvc.perform(post(BASE + "/promotions").with(user).contentType(MediaType.APPLICATION_JSON).content(offer)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/promotions/" + ID).with(user).contentType(MediaType.APPLICATION_JSON).content(offer)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/promotions/" + ID + "/deactivate").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(patch(BASE + "/supplements/" + ID + "/state").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"availability\":\"AGOTADO\",\"published\":true}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void adminKeepsStateWriteAndHistoryAccess() throws Exception {
        var admin = user("admin").roles("ADMIN");
        when(service.state(eq(ID), any(StateInput.class), eq("admin"))).thenReturn(product());
        mvc.perform(patch(BASE + "/supplements/" + ID + "/state").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"availability\":\"AGOTADO\",\"published\":true}")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/supplements/" + ID + "/history").with(admin)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/promotions/" + ID + "/history").with(admin)).andExpect(status().isOk());
        verify(service).state(eq(ID), any(StateInput.class), eq("admin"));
    }

    @Test void clientDoesNotGainOtherAdministrativeRoutes() throws Exception {
        for (var path : List.of("/api/v1/clients", "/api/v1/accounts", BASE + "/supplements/" + ID + "/extra"))
            mvc.perform(get(path).with(user("socio").roles("CLIENTE"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
