package com.gymvip.enterprise;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "branches")
public class BranchEntity {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(nullable = false, length = 60)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 100)
    private String city = "Lima";
    @Column(nullable = false, length = 100)
    private String district = "Miraflores";
    @Column(nullable = false, length = 250)
    private String address;
    @Column(length = 30)
    private String phone;
    @Column(name = "schedule_summary", length = 250)
    private String scheduleSummary;
    @Column(name = "services_summary", length = 500)
    private String servicesSummary;
    @Column(nullable = false)
    private int capacity = 120;
    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVO";
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getScheduleSummary() { return scheduleSummary; }
    public void setScheduleSummary(String scheduleSummary) { this.scheduleSummary = scheduleSummary; }
    public String getServicesSummary() { return servicesSummary; }
    public void setServicesSummary(String servicesSummary) { this.servicesSummary = servicesSummary; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
