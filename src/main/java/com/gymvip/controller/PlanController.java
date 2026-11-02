package com.gymvip.controller;

import com.gymvip.dto.request.CreatePlanRequest;
import com.gymvip.dto.request.UpdatePlanRequest;
import com.gymvip.dto.request.UpdatePlanStatusRequest;
import com.gymvip.dto.response.PlanResponse;
import com.gymvip.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listPlans(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(planService.listPlans(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.getPlanById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> createPlan(
            @Valid @RequestBody CreatePlanRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        PlanResponse response = planService.createPlan(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        PlanResponse response = planService.updatePlan(id, request, actor);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanStatusRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : "admin_sistema";
        PlanResponse response = planService.setStatus(id, request, actor);
        return ResponseEntity.ok(response);
    }
}
