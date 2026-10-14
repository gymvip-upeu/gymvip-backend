package com.gymvip.audit.application;

import com.gymvip.audit.application.port.in.AuditUseCase;
import com.gymvip.audit.application.port.out.AuditPersistencePort;
import com.gymvip.audit.domain.model.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService implements AuditUseCase {

    private final AuditPersistencePort auditPersistencePort;

    public AuditService(AuditPersistencePort auditPersistencePort) {
        this.auditPersistencePort = auditPersistencePort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent recordEvent(String actorEmail, String action, String entityName, String entityId, String details) {
        AuditEvent event = AuditEvent.create(actorEmail, action, entityName, entityId, details);
        return auditPersistencePort.save(event);
    }
}
