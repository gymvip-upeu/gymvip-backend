package com.gymvip.identity.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionJpaEntity {

    @Id
    @Column(length = 128)
    private String token;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    public SessionJpaEntity() {}

    public SessionJpaEntity(String token, UUID accountId, LocalDateTime expiresAt, LocalDateTime createdAt, String userAgent, String ipAddress) {
        this.token = token;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
