package com.gymvip.repository;

import com.gymvip.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop100ByEntityNameOrderByTimestampUtcDesc(String entityName);
    List<AuditEvent> findTop50ByEntityNameAndEntityIdOrderByTimestampUtcDesc(String entityName, String entityId);
}
