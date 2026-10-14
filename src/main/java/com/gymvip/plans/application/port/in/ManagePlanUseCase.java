package com.gymvip.plans.application.port.in;

import com.gymvip.plans.domain.model.Plan;
import com.gymvip.plans.domain.model.PlanVersion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ManagePlanUseCase {

    Plan createPlan(CreatePlanCommand command, String actorEmail);

    Plan updatePlan(UUID id, UpdatePlanCommand command, String actorEmail);

    Plan setStatus(UUID id, String status, String actorEmail);

    Plan getPlanById(UUID id);

    List<Plan> listPlans(String status);

    PlanVersion getLatestVersion(UUID planId);

    record CreatePlanCommand(
            String name,
            String description,
            int durationDays,
            BigDecimal pricePen,
            String benefits
    ) {}

    record UpdatePlanCommand(
            String name,
            String description,
            int durationDays,
            BigDecimal pricePen,
            String benefits
    ) {}
}
