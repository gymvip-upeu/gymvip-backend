package com.gymvip.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;
import static com.gymvip.catalog.CatalogDtos.*;

@RestController
@RequestMapping("/api/v1/catalog")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class CatalogController {
    private final CatalogService service;
    private final ObjectMapper json;
    private final Validator validator;
    public CatalogController(CatalogService service,ObjectMapper json,Validator validator) {this.service=service;this.json=json;this.validator=validator;}
    @GetMapping("/options") public Object options() {
        return Map.of("categories",List.of("PROTEINA","CREATINA","PREENTRENO","AMINOACIDOS","VITAMINAS","OTROS"),"maxImages",5,"maxImageBytes",CatalogImageValidator.MAX_BYTES,"whatsappConfigured",false,"timezone","America/Lima");
    }
    @GetMapping("/supplements") public Page<Supplement> list(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String category,
        @RequestParam(defaultValue="") String availability,@RequestParam(required=false) Boolean published,@RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,
        @RequestParam(defaultValue="false") boolean withPromotion,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {
        return service.supplements(query,category,availability,published,minPrice,maxPrice,withPromotion,page,size,false);
    }
    @GetMapping("/supplements/{id}") public Supplement detail(@PathVariable UUID id) {return service.supplement(id,false);}
    private SupplementInput parse(String data) {
        try {
            var input=json.readValue(data,SupplementInput.class);
            if(input==null)throw new IllegalArgumentException("Completa los datos del suplemento.");
            var errors=validator.validate(input);
            if(!errors.isEmpty())throw new IllegalArgumentException(errors.stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).map(v->v.getPropertyPath()+": "+v.getMessage()).collect(java.util.stream.Collectors.joining(" · ")));
            return input;
        }catch(JsonProcessingException ex){throw new IllegalArgumentException("Los datos del suplemento no tienen un formato válido.");}
    }
    @PostMapping(value="/supplements",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Supplement> create(@RequestPart("data") String data,@RequestPart(value="images",required=false) List<MultipartFile> files,Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveSupplement(null,parse(data),files,auth.getName()));
    }
    @PutMapping(value="/supplements/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('ADMIN')")
    public Supplement update(@PathVariable UUID id,@RequestPart("data") String data,@RequestPart(value="images",required=false) List<MultipartFile> files,Authentication auth) {
        return service.saveSupplement(id,parse(data),files,auth.getName());
    }
    @PatchMapping("/supplements/{id}/state") @PreAuthorize("hasRole('ADMIN')")
    public Supplement state(@PathVariable UUID id,@Valid @RequestBody StateInput input,Authentication auth) {return service.state(id,input,auth.getName());}
    @GetMapping("/supplements/{id}/history") @PreAuthorize("hasRole('ADMIN')")
    public Object supplementHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page) {return service.supplementHistory(id,page);}
    @GetMapping("/supplements/{id}/images/{image}") public ResponseEntity<byte[]> image(@PathVariable UUID id,@PathVariable UUID image,@RequestParam(defaultValue="true") boolean thumbnail) {
        var result=service.image(id,image,thumbnail,false);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.mediaType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(result.bytes());
    }
    @GetMapping("/promotions") public Page<Promotion> promotions(@RequestParam(defaultValue="VIGENTE") String state,@RequestParam(required=false) UUID supplementId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {return service.promotions(state,supplementId,page,size,false);}
    @GetMapping("/promotions/{id}") public Promotion promotion(@PathVariable UUID id) {return service.promotion(id,false);}
    @PostMapping("/promotions") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Promotion> createPromotion(@Valid @RequestBody PromotionInput input,Authentication auth) {return ResponseEntity.status(HttpStatus.CREATED).body(service.savePromotion(null,input,auth.getName()));}
    @PutMapping("/promotions/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Promotion updatePromotion(@PathVariable UUID id,@Valid @RequestBody PromotionInput input,Authentication auth) {return service.savePromotion(id,input,auth.getName());}
    @PostMapping("/promotions/{id}/deactivate") @PreAuthorize("hasRole('ADMIN')")
    public Promotion deactivate(@PathVariable UUID id,@Valid @RequestBody PromotionCancellation input,Authentication auth) {return service.deactivatePromotion(id,input,auth.getName());}
    @GetMapping("/promotions/{id}/history") @PreAuthorize("hasRole('ADMIN')") public Object promotionHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page) {return service.promotionHistory(id,page);}
}
