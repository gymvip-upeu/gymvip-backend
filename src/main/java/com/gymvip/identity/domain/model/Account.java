package com.gymvip.identity.domain.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Modelo de dominio puro para Cuentas de Personal y Seguridad (HU-04).
 * Libre de dependencias de frameworks.
 */
public class Account {

    private UUID id;
    private String email;
    private String passwordHash;
    private Set<String> roles = new HashSet<>();
    private String status;
    private boolean mfaEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Account() {
        this.status = "ACTIVO";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Account(UUID id, String email, String passwordHash, Set<String> roles, String status, boolean mfaEnabled) {
        this.id = id != null ? id : UUID.randomUUID();
        this.email = email != null ? email.trim().toLowerCase() : "";
        this.passwordHash = passwordHash;
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        this.status = status != null ? status.toUpperCase() : "ACTIVO";
        this.mfaEnabled = mfaEnabled;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN") || roles.contains("SUPERADMIN");
    }

    public boolean isRecepcion() {
        return roles.contains("RECEPCION");
    }

    public boolean isActive() {
        return "ACTIVO".equalsIgnoreCase(status);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Set<String> getRoles() { return Collections.unmodifiableSet(roles); }
    public void setRoles(Set<String> roles) { this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
