package com.gymvip.repository;

import com.gymvip.entity.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {
    Optional<PlanVersion> findFirstByPlanIdOrderByVersionNumberDesc(UUID planId);
    List<PlanVersion> findByPlanIdOrderByVersionNumberDesc(UUID planId);
}
