package com.gymvip.identity.infrastructure.persistence.repository;

import com.gymvip.identity.infrastructure.persistence.entity.SessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataSessionRepository extends JpaRepository<SessionJpaEntity, String> {

    @Modifying
    @Query("DELETE FROM SessionJpaEntity s WHERE s.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") UUID accountId);
}
