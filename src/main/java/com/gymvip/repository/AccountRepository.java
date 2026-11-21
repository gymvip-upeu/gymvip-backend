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

    @Query("SELECT a FROM Account a WHERE lower(a.email) = lower(:identifier) " +
           "OR a.id IN (SELECT c.accountId FROM Client c WHERE (c.emailVerified = true AND lower(c.email) = lower(:identifier)) OR upper(c.memberCode) = upper(:identifier)) " +
           "OR a.id IN (SELECT d.client.accountId FROM Document d WHERE d.documentType = 'DNI' AND d.documentNumber = :identifier)")
    Optional<Account> findByIdentifierIgnoreCase(@Param("identifier") String identifier);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT DISTINCT a FROM Account a JOIN a.roles r WHERE r IN ('ADMIN', 'RECEPCION', 'AUDITOR')")
    List<Account> findStaffAccounts();

    @Query("SELECT COUNT(a) FROM Account a JOIN a.roles r WHERE r = 'ADMIN' AND UPPER(a.status) = 'ACTIVO'")
    long countActiveAdmins();
}
