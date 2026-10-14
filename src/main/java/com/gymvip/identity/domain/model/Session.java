package com.gymvip.identity.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dominio puro para Sesión y Token de Autenticación.
 */
public class Session {

    private final String token;
    private final UUID accountId;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;
    private final String userAgent;
    private final String ipAddress;

    public Session(String token, UUID accountId, LocalDateTime expiresAt, LocalDateTime createdAt, String userAgent, String ipAddress) {
        this.token = token;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now != null ? now : LocalDateTime.now());
    }

    public String getToken() { return token; }
    public UUID getAccountId() { return accountId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
}
