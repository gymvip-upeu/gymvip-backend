package com.gymvip.controller;

import com.gymvip.enterprise.BranchEntity;
import com.gymvip.enterprise.BranchRepository;
import com.gymvip.enterprise.OrganizationEntity;
import com.gymvip.enterprise.OrganizationRepository;
import com.gymvip.entity.Plan;
import com.gymvip.repository.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseController {

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final PlanRepository planRepository;

    public EnterpriseController(OrganizationRepository organizationRepository,
                                BranchRepository branchRepository,
                                PlanRepository planRepository) {
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.planRepository = planRepository;
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
    public ResponseEntity<List<BranchEntity>> listAllBranches() {
        return ResponseEntity.ok(branchRepository.findAll());
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
    public ResponseEntity<BranchEntity> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        UUID orgId = request.organizationId();
        if (orgId == null) {
            orgId = organizationRepository.findAll().stream().findFirst().map(OrganizationEntity::getId)
                    .orElseThrow(() -> new IllegalStateException("No existe empresa base configurada."));
        }
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
        branch.setPublished(true);
        branch.setStatus("ACTIVO");
        return ResponseEntity.status(HttpStatus.CREATED).body(branchRepository.save(branch));
    }
}
