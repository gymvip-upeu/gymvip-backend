package com.gymvip.plans.infrastructure.persistence.repository;

import com.gymvip.plans.infrastructure.persistence.entity.PlanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataPlanRepository extends JpaRepository<PlanJpaEntity, UUID> {
    List<PlanJpaEntity> findByStatus(String status);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
