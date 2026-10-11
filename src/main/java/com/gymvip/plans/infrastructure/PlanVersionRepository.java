package com.gymvip.plans.infrastructure;

import com.gymvip.plans.domain.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {
    List<PlanVersion> findByPlanIdOrderByVersionNumberDesc(UUID planId);
    Optional<PlanVersion> findFirstByPlanIdOrderByVersionNumberDesc(UUID planId);
}
