package com.gymvip.service.impl;

import com.gymvip.entity.AuditEvent;
import com.gymvip.repository.AuditEventRepository;
import com.gymvip.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional
    public void recordEvent(String actorEmail, String action, String entityName, String entityId, String details) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                actorEmail,
                action,
                entityName,
                entityId,
                details,
                LocalDateTime.now()
        );
        auditEventRepository.save(event);
    }
}
