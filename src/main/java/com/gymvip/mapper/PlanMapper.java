package com.gymvip.mapper;

import com.gymvip.dto.request.CreatePlanRequest;
import com.gymvip.dto.response.PlanResponse;
import com.gymvip.entity.Plan;
import com.gymvip.entity.PlanVersion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PlanMapper {

    public Plan toEntity(CreatePlanRequest request) {
        Plan plan = new Plan(
                UUID.randomUUID(),
                request.name(),
                request.description(),
                request.durationDays(),
                request.pricePen(),
                request.benefits(),
                "ACTIVO",
                1
        );
        plan.setConditions(request.conditions());
        return plan;
    }

    public PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getDurationDays(),
                plan.getPricePen(),
                plan.getBenefits(),
                plan.getStatus(),
                plan.getVersion(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(), plan.getConditions()
        );
    }

    public PlanVersion toVersionEntity(Plan plan) {
        return PlanVersion.snapshot(plan);
    }
}
