package com.gymvip.plans.infrastructure;

import com.gymvip.plans.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Plan> findByStatus(String status);
}
