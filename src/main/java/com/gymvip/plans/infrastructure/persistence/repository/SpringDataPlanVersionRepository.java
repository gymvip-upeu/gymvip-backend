package com.gymvip.plans.infrastructure.persistence.repository;

import com.gymvip.plans.infrastructure.persistence.entity.PlanVersionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataPlanVersionRepository extends JpaRepository<PlanVersionJpaEntity, UUID> {
    Optional<PlanVersionJpaEntity> findFirstByPlanIdOrderByVersionNumberDesc(UUID planId);
    List<PlanVersionJpaEntity> findByPlanIdOrderByVersionNumberDesc(UUID planId);
}
