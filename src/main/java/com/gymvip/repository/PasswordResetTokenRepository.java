package com.gymvip.repository;

import com.gymvip.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.usedAt = CURRENT_TIMESTAMP WHERE p.accountId = :accountId AND p.usedAt IS NULL")
    void invalidateActiveTokensForAccount(@Param("accountId") UUID accountId);
}
