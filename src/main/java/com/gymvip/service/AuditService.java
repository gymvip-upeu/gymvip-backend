package com.gymvip.service;

public interface AuditService {
    void recordEvent(String actorEmail, String action, String entityName, String entityId, String details);
}
