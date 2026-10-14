package com.gymvip.plans.infrastructure.persistence.adapter;

import com.gymvip.plans.application.port.out.PlanPersistencePort;
import com.gymvip.plans.domain.model.Plan;
import com.gymvip.plans.domain.model.PlanVersion;
import com.gymvip.plans.infrastructure.persistence.entity.PlanJpaEntity;
import com.gymvip.plans.infrastructure.persistence.entity.PlanVersionJpaEntity;
import com.gymvip.plans.infrastructure.persistence.mapper.PlanPersistenceMapper;
import com.gymvip.plans.infrastructure.persistence.repository.SpringDataPlanRepository;
import com.gymvip.plans.infrastructure.persistence.repository.SpringDataPlanVersionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlanPersistenceAdapter implements PlanPersistencePort {

    private final SpringDataPlanRepository planRepository;
    private final SpringDataPlanVersionRepository planVersionRepository;

    public PlanPersistenceAdapter(SpringDataPlanRepository planRepository,
                                  SpringDataPlanVersionRepository planVersionRepository) {
        this.planRepository = planRepository;
        this.planVersionRepository = planVersionRepository;
    }

    @Override
    public Plan save(Plan plan) {
        PlanJpaEntity entityToSave;
        if (plan.getId() != null) {
            Optional<PlanJpaEntity> existingOpt = planRepository.findById(plan.getId());
            if (existingOpt.isPresent()) {
                entityToSave = existingOpt.get();
                entityToSave.setName(plan.getName());
                entityToSave.setDescription(plan.getDescription());
                entityToSave.setDurationDays(plan.getDurationDays());
                entityToSave.setPricePen(plan.getPricePen());
                entityToSave.setBenefits(plan.getBenefits());
                entityToSave.setStatus(plan.getStatus());
                entityToSave.setUpdatedAt(java.time.LocalDateTime.now());
            } else {
                entityToSave = PlanPersistenceMapper.toEntity(plan);
            }
        } else {
            entityToSave = PlanPersistenceMapper.toEntity(plan);
        }
        PlanJpaEntity saved = planRepository.saveAndFlush(entityToSave);
        return PlanPersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return planRepository.findById(id).map(PlanPersistenceMapper::toDomain);
    }

    @Override
    public List<Plan> findAll(String status) {
        if (status != null && !status.isBlank()) {
            return planRepository.findByStatus(status.toUpperCase())
                    .stream()
                    .map(PlanPersistenceMapper::toDomain)
                    .collect(Collectors.toList());
        }
        return planRepository.findAll()
                .stream()
                .map(PlanPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return planRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id) {
        return planRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Override
    public PlanVersion saveVersion(PlanVersion version) {
        PlanVersionJpaEntity entity = PlanPersistenceMapper.toEntityVersion(version);
        PlanVersionJpaEntity saved = planVersionRepository.save(entity);
        return PlanPersistenceMapper.toDomainVersion(saved);
    }

    @Override
    public Optional<PlanVersion> findLatestVersion(UUID planId) {
        return planVersionRepository.findFirstByPlanIdOrderByVersionNumberDesc(planId)
                .map(PlanPersistenceMapper::toDomainVersion);
    }

    @Override
    public List<PlanVersion> findVersionsByPlanId(UUID planId) {
        return planVersionRepository.findByPlanIdOrderByVersionNumberDesc(planId)
                .stream()
                .map(PlanPersistenceMapper::toDomainVersion)
                .collect(Collectors.toList());
    }
}
