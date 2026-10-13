package com.gymvip.plans.application;

import com.gymvip.audit.application.AuditService;
import com.gymvip.plans.domain.Plan;
import com.gymvip.plans.domain.PlanVersion;
import com.gymvip.plans.infrastructure.PlanRepository;
import com.gymvip.plans.infrastructure.PlanVersionRepository;
import com.gymvip.shared.exceptions.ConflictException;
import com.gymvip.shared.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final AuditService auditService;

    public PlanService(PlanRepository planRepository,
                       PlanVersionRepository planVersionRepository,
                       AuditService auditService) {
        this.planRepository = planRepository;
        this.planVersionRepository = planVersionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Plan> findAll(String status) {
        if (status != null && !status.isBlank()) {
            return planRepository.findByStatus(status.toUpperCase());
        }
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Plan findById(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado con ID: " + id));
    }

    @Transactional
    public Plan createPlan(String name, String description, int durationDays, BigDecimal pricePen, String benefits, String actorEmail) {
        if (planRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ya existe un plan registrado con el nombre: " + name);
        }
        if (pricePen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio en PEN debe ser mayor que 0");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("La duración debe ser de al menos 1 día");
        }
        if (name.toLowerCase().contains("mensual") && durationDays != 30) {
            throw new IllegalArgumentException("La duración de un plan mensual debe ser exactamente de 30 días");
        }

        Plan plan = new Plan(name.trim(), description, durationDays, pricePen, benefits);
        Plan savedPlan = planRepository.save(plan);

        // Save initial snapshot version
        PlanVersion initialVersion = new PlanVersion(
                savedPlan.getId(),
                1,
                savedPlan.getName(),
                savedPlan.getDurationDays(),
                savedPlan.getPricePen(),
                savedPlan.getBenefits()
        );
        planVersionRepository.save(initialVersion);

        auditService.recordEvent(actorEmail, "CREATE_PLAN", "Plan", savedPlan.getId().toString(),
                "Plan creado: " + savedPlan.getName() + " - S/ " + savedPlan.getPricePen() + " - " + durationDays + "d");

        return savedPlan;
    }

    @Transactional
    public Plan updatePlan(UUID id, String name, String description, int durationDays, BigDecimal pricePen, String benefits, String actorEmail) {
        Plan plan = findById(id);

        if (!plan.getName().equalsIgnoreCase(name) && planRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ya existe otro plan registrado con el nombre: " + name);
        }
        if (pricePen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio en PEN debe ser mayor que 0");
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("La duración debe ser de al menos 1 día");
        }
        if (name.toLowerCase().contains("mensual") && durationDays != 30) {
            throw new IllegalArgumentException("La duración de un plan mensual debe ser exactamente de 30 días");
        }

        int newVersion = plan.getVersion() + 1;
        plan.setName(name.trim());
        plan.setDescription(description);
        plan.setDurationDays(durationDays);
        plan.setPricePen(pricePen);
        plan.setBenefits(benefits);
        plan.setVersion(newVersion);
        plan.setUpdatedAt(LocalDateTime.now());

        Plan updatedPlan = planRepository.save(plan);

        // Save new version snapshot without modifying existing memberships
        PlanVersion snapshot = new PlanVersion(
                updatedPlan.getId(),
                newVersion,
                updatedPlan.getName(),
                updatedPlan.getDurationDays(),
                updatedPlan.getPricePen(),
                updatedPlan.getBenefits()
        );
        planVersionRepository.save(snapshot);

        auditService.recordEvent(actorEmail, "UPDATE_PLAN", "Plan", updatedPlan.getId().toString(),
                "Plan actualizado a versión " + newVersion + ": " + updatedPlan.getName());

        return updatedPlan;
    }

    @Transactional
    public Plan setStatus(UUID id, String status, String actorEmail) {
        Plan plan = findById(id);
        String upperStatus = status.toUpperCase();
        if (!upperStatus.equals("ACTIVO") && !upperStatus.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado no válido. Valores permitidos: ACTIVO, INACTIVO");
        }
        plan.setStatus(upperStatus);
        plan.setUpdatedAt(LocalDateTime.now());
        Plan saved = planRepository.save(plan);

        auditService.recordEvent(actorEmail, "SET_PLAN_STATUS", "Plan", saved.getId().toString(),
                "Estado de plan cambiado a: " + upperStatus);

        return saved;
    }

    @Transactional(readOnly = true)
    public PlanVersion getLatestVersion(UUID planId) {
        return planVersionRepository.findFirstByPlanIdOrderByVersionNumberDesc(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Versión del plan no encontrada para ID: " + planId));
    }
}
