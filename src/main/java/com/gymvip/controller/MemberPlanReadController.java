package com.gymvip.controller;

import com.gymvip.dto.response.PlanResponse;
import com.gymvip.service.PlanService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta comercial del socio; no expone operaciones de administración. */
@RestController
@RequestMapping("/api/v1/member-app/plans")
@PreAuthorize("hasRole('CLIENTE')")
public class MemberPlanReadController {
    private final PlanService plans;

    public MemberPlanReadController(PlanService plans) { this.plans = plans; }

    public record AvailablePlan(UUID id, String name, String description, int durationDays,
                                BigDecimal pricePen, String benefits, String conditions) {
        static AvailablePlan from(PlanResponse plan) {
            return new AvailablePlan(plan.id(), plan.name(), plan.description(), plan.durationDays(),
                    plan.pricePen(), plan.benefits(), plan.conditions());
        }
    }

    @GetMapping
    public List<AvailablePlan> list() {
        return plans.listPlans("ACTIVO").stream()
                .filter(plan -> "ACTIVO".equalsIgnoreCase(plan.status()))
                .map(AvailablePlan::from).toList();
    }
}
