package com.gymvip.plans.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_pen", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePen;

    @Column(length = 1000)
    private String benefits;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVO";

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Plan() {}

    public Plan(String name, String description, int durationDays, BigDecimal pricePen, String benefits) {
        this.name = name;
        this.description = description;
        this.durationDays = durationDays;
        this.pricePen = pricePen;
        this.benefits = benefits;
        this.status = "ACTIVO";
        this.version = 1;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public BigDecimal getPricePen() { return pricePen; }
    public void setPricePen(BigDecimal pricePen) { this.pricePen = pricePen; }
    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
