package com.gymvip.audit.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc = LocalDateTime.now();

    public AuditEvent() {}

    public AuditEvent(String actorEmail, String action, String entityName, String entityId, String details) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.details = details;
    }

    public UUID getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityName() { return entityName; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public LocalDateTime getTimestampUtc() { return timestampUtc; }
}
