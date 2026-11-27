package com.gymvip.controller;

import com.gymvip.enterprise.BranchEntity;
import com.gymvip.enterprise.BranchRepository;
import com.gymvip.enterprise.OrganizationEntity;
import com.gymvip.enterprise.OrganizationRepository;
import com.gymvip.entity.Plan;
import com.gymvip.repository.PlanRepository;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.TenantScopeResolver;
import com.gymvip.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseController {

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final PlanRepository planRepository;
    private final TenantScopeResolver scope;
    private final AccountRepository accounts;

    public EnterpriseController(OrganizationRepository organizationRepository,
                                BranchRepository branchRepository,
                                PlanRepository planRepository, TenantScopeResolver scope, AccountRepository accounts) {
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.planRepository = planRepository;
        this.scope = scope;
        this.accounts = accounts;
    }

    /**
     * Explorador público/autenticado de gimnasios publicados, sedes, horarios, servicios y rango referencial de precios.
     */
    @GetMapping("/gyms")
    public ResponseEntity<List<Map<String, Object>>> explorePublishedGyms(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district
    ) {
        List<OrganizationEntity> orgs = organizationRepository.findByStatusIgnoreCaseAndIsPublishedTrueOrderByNameAsc("ACTIVO");
        List<Plan> activePlans = planRepository.findByStatus("ACTIVO");
        BigDecimal minPrice = activePlans.stream().map(Plan::getPricePen).min(BigDecimal::compareTo).orElse(null);
        BigDecimal maxPrice = activePlans.stream().map(Plan::getPricePen).max(BigDecimal::compareTo).orElse(null);

        String query = q != null ? q.trim().toLowerCase(Locale.ROOT) : "";
        String cityFilter = city != null ? city.trim().toLowerCase(Locale.ROOT) : "";
        String districtFilter = district != null ? district.trim().toLowerCase(Locale.ROOT) : "";

        List<Map<String, Object>> result = new ArrayList<>();
        for (OrganizationEntity org : orgs) {
            List<BranchEntity> branches = branchRepository.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                    .filter(b -> b.isPublished() && "ACTIVO".equalsIgnoreCase(b.getStatus()))
                    .filter(b -> cityFilter.isEmpty() || b.getCity().toLowerCase(Locale.ROOT).contains(cityFilter))
                    .filter(b -> districtFilter.isEmpty() || b.getDistrict().toLowerCase(Locale.ROOT).contains(districtFilter))
                    .filter(b -> query.isEmpty()
                            || org.getName().toLowerCase(Locale.ROOT).contains(query)
                            || b.getName().toLowerCase(Locale.ROOT).contains(query)
                            || b.getDistrict().toLowerCase(Locale.ROOT).contains(query))
                    .toList();

            if (!branches.isEmpty() || (cityFilter.isEmpty() && districtFilter.isEmpty() && query.isEmpty())) {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", org.getId());
                dto.put("code", org.getCode());
                dto.put("name", org.getName());
                dto.put("description", org.getDescription());
                dto.put("planTier", org.getPlanTier());
                dto.put("minPricePen", minPrice);
                dto.put("maxPricePen", maxPrice);
                dto.put("branches", branches);
                result.add(dto);
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('branches.read')")
    public ResponseEntity<List<BranchEntity>> listAllBranches(Authentication auth,
            @RequestParam(required = false) UUID organizationId) {
        var actor = scope.resolveActor(auth);
        UUID orgId = scope.effectiveOrganizationId(actor, organizationId);
        var ownBranches = branchRepository.findByOrganizationIdOrderByNameAsc(orgId);
        if (scope.isCompanyWideRole(actor)) return ResponseEntity.ok(ownBranches);
        UUID assignedBranch = scope.effectiveBranchId(actor, null);
        return ResponseEntity.ok(ownBranches.stream().filter(branch -> branch.getId().equals(assignedBranch)).toList());
    }

    public record CreateBranchRequest(
            UUID organizationId,
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String city,
            @NotBlank String district,
            @NotBlank String address,
            String phone,
            String scheduleSummary,
            String servicesSummary,
            Integer capacity
    ) {}

    @PostMapping("/branches")
    @PreAuthorize("hasAuthority('branches.manage')")
    @Transactional
    public ResponseEntity<BranchEntity> createBranch(@Valid @RequestBody CreateBranchRequest request, Authentication auth) {
        var actor = scope.resolveActor(auth);
        UUID orgId = scope.effectiveOrganizationId(actor, request.organizationId());
        if (!organizationRepository.existsById(orgId)) throw new ResourceNotFoundException("Empresa no encontrada.");
        BranchEntity branch = new BranchEntity();
        branch.setId(UUID.randomUUID());
        branch.setOrganizationId(orgId);
        branch.setCode(request.code().trim().toLowerCase(Locale.ROOT));
        branch.setName(request.name().trim());
        branch.setCity(request.city().trim());
        branch.setDistrict(request.district().trim());
        branch.setAddress(request.address().trim());
        branch.setPhone(request.phone());
        branch.setScheduleSummary(request.scheduleSummary());
        branch.setServicesSummary(request.servicesSummary());
        branch.setCapacity(request.capacity() != null && request.capacity() > 0 ? request.capacity() : 100);
        branch.setPublished(false);
        branch.setStatus("ACTIVO");
        return ResponseEntity.status(HttpStatus.CREATED).body(branchRepository.save(branch));
    }

    public record BranchPublicationRequest(boolean published) {}

    @PatchMapping("/branches/{id}/publication")
    @PreAuthorize("hasAuthority('branches.manage')")
    public ResponseEntity<BranchEntity> setPublication(@PathVariable UUID id,
            @RequestBody BranchPublicationRequest request, Authentication auth) {
        var actor = scope.resolveActor(auth);
        var branch = branchRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada."));
        scope.effectiveOrganizationId(actor, branch.getOrganizationId());
        scope.effectiveBranchId(actor, id);
        if (request.published() && (!"ACTIVO".equals(branch.getStatus()) || branch.getScheduleSummary() == null
                || branch.getScheduleSummary().isBlank())) {
            throw new IllegalArgumentException("Solo se puede publicar una sede activa con horarios registrados.");
        }
        branch.setPublished(request.published());
        branch.setUpdatedAt(java.time.LocalDateTime.now());
        return ResponseEntity.ok(branchRepository.save(branch));
    }

    @PutMapping("/branches/{id}/primary")
    @PreAuthorize("hasAuthority('branches.manage')")
    @Transactional
    public ResponseEntity<Void> selectPrimaryBranch(@PathVariable UUID id, Authentication auth) {
        var actor = scope.resolveActor(auth);
        var branch = branchRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada."));
        if (!scope.effectiveOrganizationId(actor, null).equals(branch.getOrganizationId())) {
            throw new org.springframework.security.access.AccessDeniedException("Selecciona una sede de tu propia empresa.");
        }
        scope.effectiveBranchId(actor, id);
        if (!"ACTIVO".equals(branch.getStatus())) throw new IllegalArgumentException("La sede seleccionada debe estar activa.");
        actor.setPrimaryBranchId(id);
        accounts.save(actor);
        return ResponseEntity.noContent().build();
    }
}
