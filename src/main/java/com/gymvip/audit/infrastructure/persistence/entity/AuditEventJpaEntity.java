package com.gymvip.audit.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(length = 2000)
    private String details;

    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc;

    public AuditEventJpaEntity() {}

    public AuditEventJpaEntity(UUID id, String actorEmail, String action, String entityName, String entityId, String details, LocalDateTime timestampUtc) {
        this.id = id;
        this.actorEmail = actorEmail;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.details = details;
        this.timestampUtc = timestampUtc;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getTimestampUtc() { return timestampUtc; }
    public void setTimestampUtc(LocalDateTime timestampUtc) { this.timestampUtc = timestampUtc; }
}
