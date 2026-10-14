package com.gymvip.plans.application.usecase;

import com.gymvip.audit.application.port.in.AuditUseCase;
import com.gymvip.plans.application.port.in.ManagePlanUseCase;
import com.gymvip.plans.application.port.out.PlanPersistencePort;
import com.gymvip.plans.domain.exception.DuplicatePlanNameException;
import com.gymvip.plans.domain.exception.InvalidPlanException;
import com.gymvip.plans.domain.exception.PlanNotFoundException;
import com.gymvip.plans.domain.model.Plan;
import com.gymvip.plans.domain.model.PlanVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ManagePlanService implements ManagePlanUseCase {

    private final PlanPersistencePort planPersistencePort;
    private final AuditUseCase auditUseCase;

    public ManagePlanService(PlanPersistencePort planPersistencePort, AuditUseCase auditUseCase) {
        this.planPersistencePort = planPersistencePort;
        this.auditUseCase = auditUseCase;
    }

    @Override
    @Transactional
    public Plan createPlan(CreatePlanCommand command, String actorEmail) {
        if (planPersistencePort.existsByNameIgnoreCase(command.name())) {
            throw new DuplicatePlanNameException("Ya existe un plan registrado con el nombre: " + command.name());
        }

        Plan plan = new Plan(
                UUID.randomUUID(),
                command.name(),
                command.description(),
                command.durationDays(),
                command.pricePen(),
                command.benefits(),
                "ACTIVO",
                1
        );

        Plan savedPlan = planPersistencePort.save(plan);

        // Instantánea inmutable inicial v1
        PlanVersion initialVersion = PlanVersion.snapshot(savedPlan);
        planPersistencePort.saveVersion(initialVersion);

        auditUseCase.recordEvent(
                actorEmail,
                "CREATE_PLAN",
                "Plan",
                savedPlan.getId().toString(),
                "Plan creado: " + savedPlan.getName() + " | S/ " + savedPlan.getPricePen() + " | " + savedPlan.getDurationDays() + " días"
        );

        return savedPlan;
    }

    @Override
    @Transactional
    public Plan updatePlan(UUID id, UpdatePlanCommand command, String actorEmail) {
        Plan plan = getPlanById(id);

        if (planPersistencePort.existsByNameIgnoreCaseAndIdNot(command.name(), id)) {
            throw new DuplicatePlanNameException("Ya existe otro plan registrado con el nombre: " + command.name());
        }

        plan.setName(command.name());
        plan.setDescription(command.description());
        plan.setDurationDays(command.durationDays());
        plan.setPricePen(command.pricePen());
        plan.setBenefits(command.benefits());
        plan.validateInvariants();

        Plan updatedPlan = planPersistencePort.save(plan);

        // Instantánea inmutable de la nueva versión tarifaria
        PlanVersion newSnapshot = PlanVersion.snapshot(updatedPlan);
        planPersistencePort.saveVersion(newSnapshot);

        auditUseCase.recordEvent(
                actorEmail,
                "UPDATE_PLAN",
                "Plan",
                updatedPlan.getId().toString(),
                "Plan actualizado a versión " + updatedPlan.getVersion() + ": " + updatedPlan.getName() + " | S/ " + updatedPlan.getPricePen()
        );

        return updatedPlan;
    }

    @Override
    @Transactional
    public Plan setStatus(UUID id, String status, String actorEmail) {
        Plan plan = getPlanById(id);
        String upperStatus = status != null ? status.trim().toUpperCase() : "";
        if (!upperStatus.equals("ACTIVO") && !upperStatus.equals("INACTIVO")) {
            throw new InvalidPlanException("Estado no válido. Valores permitidos: ACTIVO, INACTIVO");
        }

        plan.setStatus(upperStatus);
        plan.setUpdatedAt(LocalDateTime.now());
        Plan saved = planPersistencePort.save(plan);

        auditUseCase.recordEvent(
                actorEmail,
                "SET_PLAN_STATUS",
                "Plan",
                saved.getId().toString(),
                "Estado de plan " + saved.getName() + " cambiado a: " + upperStatus
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Plan getPlanById(UUID id) {
        return planPersistencePort.findById(id)
                .orElseThrow(() -> new PlanNotFoundException("Plan no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Plan> listPlans(String status) {
        return planPersistencePort.findAll(status);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanVersion getLatestVersion(UUID planId) {
        return planPersistencePort.findLatestVersion(planId)
                .orElseThrow(() -> new PlanNotFoundException("Versión del plan no encontrada para ID: " + planId));
    }
}
