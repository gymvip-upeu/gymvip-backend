package com.gymvip.enterprise;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_branch_assignments")
public class AccountBranchAssignmentEntity {
    @Id
    private UUID id;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "branch_id", nullable = false)
    private UUID branchId;
    @Column(name = "branch_role", nullable = false, length = 60)
    private String branchRole;
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVO";
    @Column(name = "assigned_by", length = 150)
    private String assignedBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getBranchRole() { return branchRole; }
    public void setBranchRole(String branchRole) { this.branchRole = branchRole; }
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
