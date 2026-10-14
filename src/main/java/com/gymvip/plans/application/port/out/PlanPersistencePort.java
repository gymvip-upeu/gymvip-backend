package com.gymvip.plans.application.port.out;

import com.gymvip.plans.domain.model.Plan;
import com.gymvip.plans.domain.model.PlanVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanPersistencePort {

    Plan save(Plan plan);

    Optional<Plan> findById(UUID id);

    List<Plan> findAll(String status);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    PlanVersion saveVersion(PlanVersion version);

    Optional<PlanVersion> findLatestVersion(UUID planId);

    List<PlanVersion> findVersionsByPlanId(UUID planId);
}
