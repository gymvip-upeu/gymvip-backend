package com.gymvip.repository;

import com.gymvip.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    @Modifying
    @Query("DELETE FROM Session s WHERE s.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") UUID accountId);
}
