package com.gymvip.saas.controller;

import com.gymvip.saas.domain.SaasCalculations;
import com.gymvip.saas.repository.SaasPlatformRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class SaasPlatformController {

    private final SaasPlatformRepository repository;

    public SaasPlatformController(SaasPlatformRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(repository.getGlobalPlatformMetrics());
    }

    @GetMapping("/plans")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> plans() {
        return ResponseEntity.ok(repository.listSaasPlans());
    }

    @GetMapping("/licenses")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> licenses() {
        return ResponseEntity.ok(repository.listLicenses());
    }

    public record UpsertLicenseRequest(
            @NotNull UUID organizationId,
            @NotBlank String planCode,
            @NotBlank String status,
            LocalDate expiresAt,
            Integer maxBranches,
            Integer maxUsers,
            Boolean autoRenew,
            String notes
    ) {}

    @PostMapping("/licenses")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> upsertLicense(@Valid @RequestBody UpsertLicenseRequest request,
                                                             Authentication auth) {
        return ResponseEntity.ok(repository.upsertLicense(
                request.organizationId(), request.planCode(), request.status(), request.expiresAt(),
                request.maxBranches(), request.maxUsers(), request.autoRenew(), request.notes(), auth.getName()
        ));
    }

    public record UpdateLicenseStatusRequest(
            @NotBlank String planCode,
            @NotBlank String status,
            LocalDate expiresAt,
            String notes
    ) {}

    @PatchMapping("/licenses/{organizationId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateLicenseStatus(@PathVariable UUID organizationId,
                                                                   @Valid @RequestBody UpdateLicenseStatusRequest request,
                                                                   Authentication auth) {
        return ResponseEntity.ok(repository.upsertLicense(
                organizationId, request.planCode(), request.status(), request.expiresAt(),
                null, null, null, request.notes(), auth.getName()
        ));
    }

    @GetMapping("/commission-rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> commissionRules() {
        return ResponseEntity.ok(repository.listCommissionRules());
    }

    public record CreateCommissionRuleRequest(
            UUID organizationId,
            @NotBlank String operationType,
            String commissionType,
            @NotNull BigDecimal percentageRate,
            BigDecimal fixedFeePen,
            BigDecimal gatewayPassthroughPct
    ) {}

    @PostMapping("/commission-rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> createCommissionRule(@Valid @RequestBody CreateCommissionRuleRequest request,
                                                                    Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.createCommissionRule(
                request.organizationId(), request.operationType(), request.commissionType(),
                request.percentageRate(), request.fixedFeePen(), request.gatewayPassthroughPct(), auth.getName()
        ));
    }

    @GetMapping("/commission-ledger")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> commissionLedger(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(repository.listCommissionLedger(organizationId, status));
    }

    public record CalculateCommissionRequest(
            @NotNull UUID organizationId,
            @NotBlank String operationType,
            @NotNull UUID sourceOperationId,
            String sourceReference,
            @NotBlank String paymentStatus,
            @NotNull BigDecimal grossAmountPen,
            String settledPeriod,
            String notes
    ) {}

    @PostMapping("/commission-ledger/calculate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> calculateLedger(@Valid @RequestBody CalculateCommissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.recordSettledCommission(
                request.organizationId(), request.operationType(), request.sourceOperationId(),
                request.sourceReference(), request.paymentStatus(), request.grossAmountPen(),
                request.settledPeriod(), request.notes()
        ));
    }

    public record UpdateLedgerStatusRequest(@NotBlank String status, String notes) {}

    @PatchMapping("/commission-ledger/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateLedgerStatus(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateLedgerStatusRequest request) {
        return ResponseEntity.ok(repository.updateCommissionLedgerStatus(id, request.status(), request.notes()));
    }

    public record SimulatorRequest(
            int activeGyms,
            BigDecimal avgMonthlySaaSFeePen,
            int avgMembersPerGym,
            BigDecimal avgTicketPen,
            BigDecimal digitalSharePct,
            BigDecimal platformCommissionPct,
            BigDecimal gatewayCostPct,
            BigDecimal monthlyInfraCostPen,
            BigDecimal fixedOpsCostPen,
            BigDecimal supportCostPerGymPen
    ) {}

    @PostMapping("/simulator/calculate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SaasCalculations.SimulatorProjection> simulate(@RequestBody SimulatorRequest request) {
        return ResponseEntity.ok(SaasCalculations.simulateB2B2C(
                request.activeGyms(),
                request.avgMonthlySaaSFeePen(),
                request.avgMembersPerGym(),
                request.avgTicketPen(),
                request.digitalSharePct(),
                request.platformCommissionPct(),
                request.gatewayCostPct(),
                request.monthlyInfraCostPen(),
                request.fixedOpsCostPen(),
                request.supportCostPerGymPen()
        ));
    }

    @GetMapping("/company-requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> companyRequests(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(repository.listCompanyRequests(status));
    }

    public record ReviewRequest(@NotBlank String status, String notes) {}

    @PatchMapping("/company-requests/{id}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> reviewCompanyRequest(@PathVariable UUID id,
                                                                    @Valid @RequestBody ReviewRequest request,
                                                                    Authentication auth) {
        return ResponseEntity.ok(repository.reviewCompanyRequest(id, request.status(), request.notes(), auth.getName()));
    }

    @PostMapping("/company-requests/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> approveCompanyRequest(@PathVariable UUID id,
                                                                     @RequestParam(defaultValue = "true") boolean startTrial,
                                                                     Authentication auth) {
        return ResponseEntity.ok(repository.approveAndProvisionCompanyRequest(id, auth.getName(), startTrial));
    }

    @GetMapping("/export/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN_EMPRESA','GERENTE_GENERAL','AUDITOR_EMPRESA','ADMIN')")
    public ResponseEntity<Map<String, Object>> exportOrganizationPortable(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(repository.exportOrganizationPortableSummary(organizationId));
    }
}
