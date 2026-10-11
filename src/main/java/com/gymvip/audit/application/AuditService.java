package com.gymvip.audit.application;

import com.gymvip.audit.domain.AuditEvent;
import com.gymvip.audit.infrastructure.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String actorEmail, String action, String entityName, String entityId, String details) {
        AuditEvent event = new AuditEvent(actorEmail, action, entityName, entityId, details);
        auditRepository.save(event);
    }
}
