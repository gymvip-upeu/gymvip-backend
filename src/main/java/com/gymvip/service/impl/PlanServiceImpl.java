package com.gymvip.service.impl;

import com.gymvip.dto.request.CreatePlanRequest;
import com.gymvip.dto.request.UpdatePlanRequest;
import com.gymvip.dto.request.UpdatePlanStatusRequest;
import com.gymvip.dto.response.PlanResponse;
import com.gymvip.entity.Plan;
import com.gymvip.entity.PlanVersion;
import com.gymvip.enums.PlanStatus;
import com.gymvip.exception.DuplicatePlanNameException;
import com.gymvip.exception.InvalidPlanException;
import com.gymvip.exception.PlanNotFoundException;
import com.gymvip.mapper.PlanMapper;
import com.gymvip.repository.PlanRepository;
import com.gymvip.repository.PlanVersionRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.PlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanMapper planMapper;
    private final AuditService auditService;

    public PlanServiceImpl(PlanRepository planRepository,
                           PlanVersionRepository planVersionRepository,
                           PlanMapper planMapper,
                           AuditService auditService) {
        this.planRepository = planRepository;
        this.planVersionRepository = planVersionRepository;
        this.planMapper = planMapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request, String actorEmail) {
        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (planRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicatePlanNameException("Ya existe un plan registrado con el nombre: " + trimmedName);
        }

        Plan plan = planMapper.toEntity(request);
        plan.validateInvariants();

        Plan saved = planRepository.save(plan);

        PlanVersion versionSnapshot = PlanVersion.snapshot(saved);
        planVersionRepository.save(versionSnapshot);

        auditService.recordEvent(
                actorEmail,
                "CREATE_PLAN",
                "Plan",
                saved.getId().toString(),
                "Plan creado: " + saved.getName() + " | S/ " + saved.getPricePen() + " | " + saved.getDurationDays() + " días"
        );

        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request, String actorEmail) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException("Plan no encontrado con ID: " + id));

        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (planRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new DuplicatePlanNameException("Ya existe otro plan registrado con el nombre: " + trimmedName);
        }

        plan.setName(trimmedName);
        plan.setDescription(request.description() != null ? request.description().trim() : null);
        plan.setDurationDays(request.durationDays());
        plan.setPricePen(request.pricePen());
        plan.setBenefits(request.benefits() != null ? request.benefits().trim() : null);
        plan.validateInvariants();
        plan.incrementVersion();

        Plan updated = planRepository.save(plan);

        PlanVersion versionSnapshot = PlanVersion.snapshot(updated);
        planVersionRepository.save(versionSnapshot);

        auditService.recordEvent(
                actorEmail,
                "UPDATE_PLAN",
                "Plan",
                updated.getId().toString(),
                "Plan actualizado a versión " + updated.getVersion() + ": " + updated.getName() + " | S/ " + updated.getPricePen()
        );

        return planMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public PlanResponse setStatus(UUID id, UpdatePlanStatusRequest request, String actorEmail) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new PlanNotFoundException("Plan no encontrado con ID: " + id));

        String targetStatus = request.status() != null ? request.status().trim().toUpperCase() : "";
        if (!targetStatus.equals(PlanStatus.ACTIVO.name()) && !targetStatus.equals(PlanStatus.INACTIVO.name())) {
            throw new InvalidPlanException("Estado no válido: " + targetStatus + ". Valores permitidos: ACTIVO, INACTIVO");
        }

        plan.setStatus(targetStatus);
        plan.setUpdatedAt(LocalDateTime.now());
        Plan saved = planRepository.save(plan);

        auditService.recordEvent(
                actorEmail,
                "SET_PLAN_STATUS",
                "Plan",
                saved.getId().toString(),
                "Estado de plan " + saved.getName() + " cambiado a: " + targetStatus
        );

        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponse getPlanById(UUID id) {
        return planRepository.findById(id)
                .map(planMapper::toResponse)
                .orElseThrow(() -> new PlanNotFoundException("Plan no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(String status) {
        List<Plan> plans;
        if (status != null && !status.isBlank()) {
            plans = planRepository.findByStatus(status.trim().toUpperCase());
        } else {
            plans = planRepository.findAll();
        }
        return plans.stream().map(planMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanVersion getLatestVersion(UUID planId) {
        return planVersionRepository.findFirstByPlanIdOrderByVersionNumberDesc(planId)
                .orElseThrow(() -> new PlanNotFoundException("Versión del plan no encontrada para ID: " + planId));
    }
}
