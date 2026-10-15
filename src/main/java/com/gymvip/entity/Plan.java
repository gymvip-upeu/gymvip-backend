package com.gymvip.entity;

import com.gymvip.exception.InvalidPlanException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "price_pen", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePen;

    @Column(length = 1000)
    private String benefits;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVO";

    @Version
    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Plan() {}

    public Plan(UUID id, String name, String description, int durationDays, BigDecimal pricePen, String benefits, String status, int version) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name != null ? name.trim() : "";
        this.description = description != null ? description.trim() : null;
        this.durationDays = durationDays;
        this.pricePen = pricePen;
        this.benefits = benefits != null ? benefits.trim() : null;
        this.status = status != null ? status.toUpperCase() : "ACTIVO";
        this.version = version > 0 ? version : 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        validateInvariants();
    }

    public void validateInvariants() {
        if (name == null || name.isBlank()) {
            throw new InvalidPlanException("El nombre del plan no puede estar vacío");
        }
        if (pricePen == null || pricePen.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPlanException("El precio en PEN debe ser mayor que 0");
        }
        if (durationDays <= 0) {
            throw new InvalidPlanException("La duración debe ser de al menos 1 día");
        }
        
        // Regla HU-06: la mensualidad en GYM VIP es de exactamente 30 días.
        // No depende únicamente de la subcadena "mensual", sino también de variaciones semánticas ("mes", "1 mes", "monthly")
        // o rangos de duración que pretendan modelar un mes calendario (28 a 31 días).
        String lowerName = name.toLowerCase();
        boolean isMonthlyByName = lowerName.contains("mensual") || lowerName.matches(".*\\b(mes|1\\s*mes|monthly)\\b.*");
        boolean isMonthlyByDurationRange = durationDays >= 28 && durationDays <= 31;
        if ((isMonthlyByName || isMonthlyByDurationRange) && durationDays != 30) {
            throw new InvalidPlanException("La duración de un plan mensual debe ser exactamente de 30 días");
        }
    }

    public void incrementVersion() {
        this.version++;
        this.updatedAt = LocalDateTime.now();
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
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
