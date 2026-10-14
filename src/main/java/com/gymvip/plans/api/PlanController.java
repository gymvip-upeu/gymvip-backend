package com.gymvip.plans.api;

import com.gymvip.plans.api.dto.CreatePlanRequest;
import com.gymvip.plans.api.dto.PlanResponse;
import com.gymvip.plans.api.dto.UpdatePlanRequest;
import com.gymvip.plans.api.dto.UpdateStatusRequest;
import com.gymvip.plans.api.mapper.PlanApiMapper;
import com.gymvip.plans.application.port.in.ManagePlanUseCase;
import com.gymvip.plans.domain.model.Plan;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final ManagePlanUseCase managePlanUseCase;

    public PlanController(ManagePlanUseCase managePlanUseCase) {
        this.managePlanUseCase = managePlanUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<PlanResponse>> listPlans(@RequestParam(value = "status", required = false) String status) {
        List<PlanResponse> responses = managePlanUseCase.listPlans(status).stream()
                .map(PlanApiMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID id) {
        Plan plan = managePlanUseCase.getPlanById(id);
        return ResponseEntity.ok(PlanApiMapper.toResponse(plan));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<PlanResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        ManagePlanUseCase.CreatePlanCommand command = new ManagePlanUseCase.CreatePlanCommand(
                request.name(),
                request.description(),
                request.durationDays(),
                request.pricePen(),
                request.benefits()
        );
        Plan created = managePlanUseCase.createPlan(command, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanApiMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        ManagePlanUseCase.UpdatePlanCommand command = new ManagePlanUseCase.UpdatePlanCommand(
                request.name(),
                request.description(),
                request.durationDays(),
                request.pricePen(),
                request.benefits()
        );
        Plan updated = managePlanUseCase.updatePlan(id, command, actor);
        return ResponseEntity.ok(PlanApiMapper.toResponse(updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<PlanResponse> setStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "anonimo";
        Plan updated = managePlanUseCase.setStatus(id, request.status(), actor);
        return ResponseEntity.ok(PlanApiMapper.toResponse(updated));
    }
}
