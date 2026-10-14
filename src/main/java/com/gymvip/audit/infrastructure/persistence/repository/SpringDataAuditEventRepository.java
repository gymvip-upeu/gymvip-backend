package com.gymvip.audit.infrastructure.persistence.repository;

import com.gymvip.audit.infrastructure.persistence.entity.AuditEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataAuditEventRepository extends JpaRepository<AuditEventJpaEntity, UUID> {
}
