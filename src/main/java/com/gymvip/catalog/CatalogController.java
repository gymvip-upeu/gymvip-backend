package com.gymvip.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.exception.ResourceNotFoundException;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.service.SupplementStockService;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_EMPRESA','ADMIN','GERENTE_GENERAL','GERENTE','GERENTE_SEDE','RECEPCION','CLIENTE')")
public class CatalogController {
    private final CatalogService service;
    private final ObjectMapper json;
    private final Validator validator;
    private final TenantScopeResolver tenants;
    private final SupplementStockService stock;

    public CatalogController(CatalogService service, ObjectMapper json, Validator validator,
                             TenantScopeResolver tenants, SupplementStockService stock) {
        this.service = service;
        this.json = json;
        this.validator = validator;
        this.tenants = tenants;
        this.stock = stock;
    }

    private MemberSupplementStock scope(Authentication auth, UUID branchId) {
        if (isCustomer(auth)) return service.memberStock(auth.getName());
        UUID branch = tenants.effectiveBranchId(tenants.resolveActor(auth), branchId);
        return new MemberSupplementStock(branch, TenantScopeResolver.DEFAULT_BRANCH_ID.equals(branch));
    }

    private void checkProduct(UUID id, Authentication auth) {
        UUID branch = stock.productBranch(id);
        if (isCustomer(auth)) {
            if (!Objects.equals(branch, scope(auth, null).branchId())) {
                throw new ResourceNotFoundException("Suplemento no encontrado.");
            }
        } else {
            tenants.effectiveBranchId(tenants.resolveActor(auth), branch);
        }
    }

    private Promotion checkedPromotion(UUID id, Authentication auth) {
        Promotion item = service.promotion(id, isCustomer(auth));
        checkProduct(item.supplement().id(), auth);
        return item;
    }

    private boolean isCustomer(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
    }

    @GetMapping("/options") public Object options() {
        return Map.of("categories",List.of("PROTEINA","CREATINA","PREENTRENO","AMINOACIDOS","VITAMINAS","OTROS"),"maxImages",5,"maxImageBytes",CatalogImageValidator.MAX_BYTES,"whatsappConfigured",false,"timezone","America/Lima");
    }

    @GetMapping("/products")
    public List<MobileProduct> products(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) UUID branchId, Authentication auth) {
        return service.mobileProductsForBranch(page, size, isCustomer(auth), scope(auth, branchId));
    }

    @GetMapping("/supplements") public Page<Supplement> list(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="") String category,
        @RequestParam(defaultValue="") String availability,@RequestParam(required=false) Boolean published,@RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,
        @RequestParam(defaultValue="false") boolean withPromotion,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size, @RequestParam(required=false) UUID branchId, Authentication auth) {
        boolean cust = isCustomer(auth);
        Boolean filterPublished = cust ? Boolean.TRUE : published;
        return service.supplements(query,category,availability,filterPublished,minPrice,maxPrice,withPromotion,page,size,cust,scope(auth,branchId));
    }
    @GetMapping("/supplements/{id}") public Supplement detail(@PathVariable UUID id, Authentication auth) {checkProduct(id,auth); return service.supplement(id,isCustomer(auth));}
    private SupplementInput parse(String data) {
        try {
            var input=json.readValue(data,SupplementInput.class);
            if(input==null)throw new IllegalArgumentException("Completa los datos del suplemento.");
            var errors=validator.validate(input);
            if(!errors.isEmpty())throw new IllegalArgumentException(errors.stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).map(v->v.getPropertyPath()+": "+v.getMessage()).collect(java.util.stream.Collectors.joining(" · ")));
            return input;
        }catch(JsonProcessingException ex){throw new IllegalArgumentException("Los datos del suplemento no tienen un formato válido.");}
    }
    @PostMapping(value="/supplements",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public ResponseEntity<Supplement> create(@RequestPart("data") String data,@RequestPart(value="images",required=false) List<MultipartFile> files,@RequestParam(required=false) UUID branchId,Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveSupplement(null,parse(data),files,auth.getName(),scope(auth,branchId).branchId()));
    }
    @PutMapping(value="/supplements/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public Supplement update(@PathVariable UUID id,@RequestPart("data") String data,@RequestPart(value="images",required=false) List<MultipartFile> files,Authentication auth) {
        checkProduct(id,auth);
        return service.saveSupplement(id,parse(data),files,auth.getName());
    }
    @PatchMapping("/supplements/{id}/state") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public Supplement state(@PathVariable UUID id,@Valid @RequestBody StateInput input,Authentication auth) {checkProduct(id,auth); return service.state(id,input,auth.getName());}
    @GetMapping("/supplements/{id}/history") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public Object supplementHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page, Authentication auth) {checkProduct(id,auth); return service.supplementHistory(id,page);}
    @GetMapping("/supplements/{id}/images/{image}") public ResponseEntity<byte[]> image(@PathVariable UUID id,@PathVariable UUID image,@RequestParam(defaultValue="true") boolean thumbnail, Authentication auth) {
        checkProduct(id,auth);
        var result=service.image(id,image,thumbnail,isCustomer(auth));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.mediaType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(result.bytes());
    }
    @GetMapping("/promotions") public Page<Promotion> promotions(@RequestParam(defaultValue="VIGENTE") String state,@RequestParam(required=false) UUID supplementId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size, Authentication auth) {return service.promotions(state,supplementId,page,size,isCustomer(auth),scope(auth,null));}
    @GetMapping("/promotions/{id}") public Promotion promotion(@PathVariable UUID id, Authentication auth) {return checkedPromotion(id,auth);}
    @PostMapping("/promotions") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public ResponseEntity<Promotion> createPromotion(@Valid @RequestBody PromotionInput input,Authentication auth) {checkProduct(input.supplementId(),auth); return ResponseEntity.status(HttpStatus.CREATED).body(service.savePromotion(null,input,auth.getName()));}
    @PutMapping("/promotions/{id}") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public Promotion updatePromotion(@PathVariable UUID id,@Valid @RequestBody PromotionInput input,Authentication auth) {checkedPromotion(id,auth); checkProduct(input.supplementId(),auth); return service.savePromotion(id,input,auth.getName());}
    @PostMapping("/promotions/{id}/deactivate") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')")
    public Promotion deactivate(@PathVariable UUID id,@Valid @RequestBody PromotionCancellation input,Authentication auth) {checkedPromotion(id,auth); return service.deactivatePromotion(id,input,auth.getName());}
    @GetMapping("/promotions/{id}/history") @PreAuthorize("hasAnyRole('ADMIN','ADMIN_EMPRESA','GERENTE','GERENTE_GENERAL')") public Object promotionHistory(@PathVariable UUID id,@RequestParam(defaultValue="0") int page, Authentication auth) {checkedPromotion(id,auth); return service.promotionHistory(id,page);}

    @GetMapping("/supplements/scan/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCION')")
    public Supplement scanProduct(@PathVariable String code, Authentication auth) {
        Supplement item = service.scan(code);
        checkProduct(item.id(),auth);
        return item;
    }

    @PatchMapping("/supplements/{id}/barcode")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCION')")
    public Supplement updateBarcode(@PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        checkProduct(id,auth);
        return service.updateBarcode(id, body != null ? body.get("barcode") : null, auth.getName());
    }
}
