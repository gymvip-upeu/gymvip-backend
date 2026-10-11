package com.gymvip.plans.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plan_versions")
public class PlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_pen", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePen;

    @Column(length = 1000)
    private String benefits;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    public PlanVersion() {}

    public PlanVersion(UUID planId, int versionNumber, String name, int durationDays, BigDecimal pricePen, String benefits) {
        this.planId = planId;
        this.versionNumber = versionNumber;
        this.name = name;
        this.durationDays = durationDays;
        this.pricePen = pricePen;
        this.benefits = benefits;
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
