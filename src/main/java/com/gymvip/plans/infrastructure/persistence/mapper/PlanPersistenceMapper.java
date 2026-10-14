package com.gymvip.plans.infrastructure.persistence.mapper;

import com.gymvip.plans.domain.model.Plan;
import com.gymvip.plans.domain.model.PlanVersion;
import com.gymvip.plans.infrastructure.persistence.entity.PlanJpaEntity;
import com.gymvip.plans.infrastructure.persistence.entity.PlanVersionJpaEntity;

public final class PlanPersistenceMapper {

    private PlanPersistenceMapper() {}

    public static Plan toDomain(PlanJpaEntity entity) {
        if (entity == null) return null;
        Plan plan = new Plan(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDurationDays(),
                entity.getPricePen(),
                entity.getBenefits(),
                entity.getStatus(),
                entity.getVersion()
        );
        plan.setCreatedAt(entity.getCreatedAt());
        plan.setUpdatedAt(entity.getUpdatedAt());
        return plan;
    }

    public static PlanJpaEntity toEntity(Plan domain) {
        if (domain == null) return null;
        PlanJpaEntity entity = new PlanJpaEntity(
                domain.getId(),
                domain.getName(),
                domain.getDescription(),
                domain.getDurationDays(),
                domain.getPricePen(),
                domain.getBenefits(),
                domain.getStatus(),
                domain.getVersion()
        );
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public static PlanVersion toDomainVersion(PlanVersionJpaEntity entity) {
        if (entity == null) return null;
        return new PlanVersion(
                entity.getId(),
                entity.getPlanId(),
                entity.getVersionNumber(),
                entity.getName(),
                entity.getDurationDays(),
                entity.getPricePen(),
                entity.getBenefits(),
                entity.getEffectiveFrom()
        );
    }

    public static PlanVersionJpaEntity toEntityVersion(PlanVersion domain) {
        if (domain == null) return null;
        return new PlanVersionJpaEntity(
                domain.getId(),
                domain.getPlanId(),
                domain.getVersionNumber(),
                domain.getName(),
                domain.getDurationDays(),
                domain.getPricePen(),
                domain.getBenefits(),
                domain.getEffectiveFrom()
        );
    }
}
