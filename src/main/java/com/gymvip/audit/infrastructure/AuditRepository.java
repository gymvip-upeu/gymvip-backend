package com.gymvip.audit.infrastructure;

import com.gymvip.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
}
