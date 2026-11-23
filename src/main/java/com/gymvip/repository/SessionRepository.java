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

    @Modifying
    @Query("DELETE FROM Session s WHERE s.accountId = :accountId AND s.token != :currentToken")
    void deleteByAccountIdAndTokenNot(@Param("accountId") UUID accountId, @Param("currentToken") String currentToken);

    @Modifying
    @Query(value = "UPDATE member_device_tokens SET is_active = false, updated_at_utc = CURRENT_TIMESTAMP WHERE client_id IN (SELECT id FROM clients WHERE account_id = :accountId)", nativeQuery = true)
    void deactivateMemberDevices(@Param("accountId") UUID accountId);
}
