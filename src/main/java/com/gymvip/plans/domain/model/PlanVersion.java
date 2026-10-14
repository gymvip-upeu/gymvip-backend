package com.gymvip.plans.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dominio puro para Versión Histórica de Plan (PlanVersion).
 * Instantánea inmutable de las condiciones tarifarias y de cobertura.
 */
public class PlanVersion {

    private final UUID id;
    private final UUID planId;
    private final int versionNumber;
    private final String name;
    private final int durationDays;
    private final BigDecimal pricePen;
    private final String benefits;
    private final LocalDateTime effectiveFrom;

    public PlanVersion(UUID id, UUID planId, int versionNumber, String name, int durationDays, BigDecimal pricePen, String benefits, LocalDateTime effectiveFrom) {
        this.id = id != null ? id : UUID.randomUUID();
        this.planId = planId;
        this.versionNumber = versionNumber;
        this.name = name;
        this.durationDays = durationDays;
        this.pricePen = pricePen;
        this.benefits = benefits;
        this.effectiveFrom = effectiveFrom != null ? effectiveFrom : LocalDateTime.now();
    }

    public static PlanVersion snapshot(Plan plan) {
        return new PlanVersion(
                UUID.randomUUID(),
                plan.getId(),
                plan.getVersion(),
                plan.getName(),
                plan.getDurationDays(),
                plan.getPricePen(),
                plan.getBenefits(),
                LocalDateTime.now()
        );
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public int getVersionNumber() { return versionNumber; }
    public String getName() { return name; }
    public int getDurationDays() { return durationDays; }
    public BigDecimal getPricePen() { return pricePen; }
    public String getBenefits() { return benefits; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
}
