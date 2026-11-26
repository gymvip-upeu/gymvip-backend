package com.gymvip.enterprise;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferenceEntity {
    @Id
    @Column(name = "account_id")
    private UUID accountId;
    @Column(name = "show_guides", nullable = false)
    private boolean showGuides = true;
    @Column(name = "experience_mode", nullable = false, length = 20)
    private String experienceMode = "GUIDED";
    @Column(name = "preferred_branch_id")
    private UUID preferredBranchId;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public boolean isShowGuides() { return showGuides; }
    public void setShowGuides(boolean showGuides) { this.showGuides = showGuides; }
    public String getExperienceMode() { return experienceMode; }
    public void setExperienceMode(String experienceMode) { this.experienceMode = experienceMode; }
    public UUID getPreferredBranchId() { return preferredBranchId; }
    public void setPreferredBranchId(UUID preferredBranchId) { this.preferredBranchId = preferredBranchId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
