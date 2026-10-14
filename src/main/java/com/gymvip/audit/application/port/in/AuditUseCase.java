package com.gymvip.audit.application.port.in;

import com.gymvip.audit.domain.model.AuditEvent;

public interface AuditUseCase {
    AuditEvent recordEvent(String actorEmail, String action, String entityName, String entityId, String details);
}
