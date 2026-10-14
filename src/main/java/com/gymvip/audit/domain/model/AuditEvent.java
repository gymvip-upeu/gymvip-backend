package com.gymvip.audit.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dominio puro para eventos de auditoría (Audit Log).
 * Inmutable y libre de dependencias de frameworks.
 */
public class AuditEvent {

    private final UUID id;
    private final String actorEmail;
    private final String action;
    private final String entityName;
    private final String entityId;
    private final String details;
    private final LocalDateTime timestampUtc;

    public AuditEvent(UUID id, String actorEmail, String action, String entityName, String entityId, String details, LocalDateTime timestampUtc) {
        this.id = id != null ? id : UUID.randomUUID();
        this.actorEmail = actorEmail != null ? actorEmail : "sistema";
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.details = details;
        this.timestampUtc = timestampUtc != null ? timestampUtc : LocalDateTime.now();
    }

    public static AuditEvent create(String actorEmail, String action, String entityName, String entityId, String details) {
        return new AuditEvent(UUID.randomUUID(), actorEmail, action, entityName, entityId, details, LocalDateTime.now());
    }

    public UUID getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityName() { return entityName; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public LocalDateTime getTimestampUtc() { return timestampUtc; }
}
