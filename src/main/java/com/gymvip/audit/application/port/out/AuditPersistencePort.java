package com.gymvip.audit.application.port.out;

import com.gymvip.audit.domain.model.AuditEvent;
import java.util.List;

public interface AuditPersistencePort {
    AuditEvent save(AuditEvent event);
    List<AuditEvent> findRecent(int limit);
}
