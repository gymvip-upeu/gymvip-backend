package com.gymvip.plans.api;

import com.gymvip.plans.application.PlanService;
import com.gymvip.plans.domain.Plan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    public record CreatePlanRequest(
            @NotBlank(message = "El nombre del plan no puede estar vacío")
            String name,
            String description,
            @NotNull(message = "La duración es obligatoria")
            @Min(value = 1, message = "La duración debe ser de al menos 1 día")
            Integer durationDays,
            @NotNull(message = "El precio es obligatorio")
            @Positive(message = "El precio en PEN debe ser mayor a 0")
            BigDecimal pricePen,
            String benefits
    ) {}

    public record UpdateStatusRequest(
            @NotBlank(message = "El estado es requerido")
            String status
    ) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Plan>> listPlans(@RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(planService.findAll(status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Plan> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Plan> createPlan(@Valid @RequestBody CreatePlanRequest request, Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Plan created = planService.createPlan(
                request.name(),
                request.description(),
                request.durationDays(),
                request.pricePen(),
                request.benefits(),
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Plan> updatePlan(@PathVariable UUID id,
                                           @Valid @RequestBody CreatePlanRequest request,
                                           Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Plan updated = planService.updatePlan(
                id,
                request.name(),
                request.description(),
                request.durationDays(),
                request.pricePen(),
                request.benefits(),
                actor
        );
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Plan> setStatus(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateStatusRequest request,
                                          Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Plan updated = planService.setStatus(id, request.status(), actor);
        return ResponseEntity.ok(updated);
    }
}
