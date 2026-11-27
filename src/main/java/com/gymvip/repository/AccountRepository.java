package com.gymvip.repository;

import com.gymvip.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmailIgnoreCase(String email);

    @Query("SELECT a FROM Account a WHERE a.id IN (SELECT c.accountId FROM Client c WHERE c.emailVerified = true AND lower(c.email) = lower(:email))")
    List<Account> findByVerifiedMemberEmail(@Param("email") String email);

    @Query("SELECT a FROM Account a WHERE lower(a.email) = lower(:identifier) " +
           "OR a.id IN (SELECT c.accountId FROM Client c WHERE (c.emailVerified = true AND lower(c.email) = lower(:identifier)) OR upper(c.memberCode) = upper(:identifier)) " +
           "OR a.id IN (SELECT d.client.accountId FROM Document d WHERE d.documentType = 'DNI' AND d.documentNumber = :identifier)")
    Optional<Account> findByIdentifierIgnoreCase(@Param("identifier") String identifier);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            UPDATE accounts SET first_access_original_hash=password_hash, password_hash=:retiredHash,
                first_access_password_consumed_at=NOW()
            WHERE id=:id AND password_hash=:originalHash AND status='ACTIVO'
              AND first_access_password AND must_change_password
              AND first_access_password_consumed_at IS NULL
              AND temporary_password_expires_at > (NOW() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    int consumeFirstAccessPassword(@Param("id") UUID id, @Param("originalHash") String originalHash,
                                   @Param("retiredHash") String retiredHash);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT DISTINCT a FROM Account a JOIN a.roles r WHERE r <> 'CLIENTE'")
    List<Account> findStaffAccounts();

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(535872169317)", nativeQuery = true)
    int lockStaffRoleChanges();

    @Query(value = """
            SELECT count(DISTINCT a.id) FROM accounts a JOIN account_roles r ON r.account_id = a.id
            WHERE UPPER(a.status) = 'ACTIVO'
              AND COALESCE(a.organization_id, CAST('11111111-1111-4111-8111-111111111111' AS uuid)) = :organizationId
              AND r.role_id IN ('ADMIN', 'ADMIN_EMPRESA')
            """, nativeQuery = true)
    long countActiveCompanyAdmins(@Param("organizationId") UUID organizationId);

    @Query("SELECT COUNT(DISTINCT a.id) FROM Account a JOIN a.roles r WHERE r IN ('ADMIN', 'SUPER_ADMIN') AND UPPER(a.status) = 'ACTIVO'")
    long countActiveAdmins();

    @Query("SELECT COUNT(DISTINCT a.id) FROM Account a JOIN a.roles r WHERE r = 'SUPER_ADMIN' AND UPPER(a.status) = 'ACTIVO'")
    long countActiveSuperAdmins();
}
