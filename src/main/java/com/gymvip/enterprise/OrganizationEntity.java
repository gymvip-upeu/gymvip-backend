package com.gymvip.enterprise;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class OrganizationEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 60)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "tax_id", length = 30)
    private String taxId;
    @Column(length = 500)
    private String description;
    @Column(name = "plan_tier", nullable = false, length = 40)
    private String planTier = "ENTERPRISE";
    @Column(nullable = false, length = 30)
    private String status = "ACTIVO";
    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPlanTier() { return planTier; }
    public void setPlanTier(String planTier) { this.planTier = planTier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
