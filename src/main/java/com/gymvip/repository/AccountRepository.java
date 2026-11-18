package com.gymvip.repository;

import com.gymvip.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmailIgnoreCase(String email);

    @Query("SELECT a FROM Account a WHERE lower(a.email) = lower(:identifier) " +
           "OR a.id IN (SELECT c.accountId FROM Client c WHERE lower(c.email) = lower(:identifier) OR upper(c.memberCode) = upper(:identifier))")
    Optional<Account> findByIdentifierIgnoreCase(@Param("identifier") String identifier);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(a) FROM Account a JOIN a.roles r WHERE r = 'ADMIN' AND UPPER(a.status) = 'ACTIVO'")
    long countActiveAdmins();
}
