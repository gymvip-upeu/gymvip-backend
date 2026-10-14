package com.gymvip.plans.api.mapper;

import com.gymvip.plans.api.dto.PlanResponse;
import com.gymvip.plans.domain.model.Plan;

public final class PlanApiMapper {

    private PlanApiMapper() {}

    public static PlanResponse toResponse(Plan plan) {
        if (plan == null) return null;
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
                plan.getUpdatedAt()
        );
    }
}
