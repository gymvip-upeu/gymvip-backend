package com.gymvip.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plan_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_versions_plan_version", columnNames = {"plan_id", "version_number"})
})
public class PlanVersion {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_pen", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePen;

    @Column(length = 1000)
    private String benefits;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    public PlanVersion() {}

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
    public void setId(UUID id) { this.id = id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public BigDecimal getPricePen() { return pricePen; }
    public void setPricePen(BigDecimal pricePen) { this.pricePen = pricePen; }
    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
}
