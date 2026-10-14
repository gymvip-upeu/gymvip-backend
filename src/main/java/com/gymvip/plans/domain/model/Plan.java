package com.gymvip.plans.domain.model;

import com.gymvip.plans.domain.exception.InvalidPlanException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dominio puro para Plan de Membresía de GYM VIP.
 * Contiene invariantes de negocio: duración mensual de 30 días exactos,
 * precios positivos en PEN y versionado tarifario.
 * Libre de frameworks (sin Spring ni JPA).
 */
public class Plan {

    private UUID id;
    private String name;
    private String description;
    private int durationDays;
    private BigDecimal pricePen;
    private String benefits;
    private String status;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Plan() {
        this.status = "ACTIVO";
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

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
        if (name.toLowerCase().contains("mensual") && durationDays != 30) {
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
