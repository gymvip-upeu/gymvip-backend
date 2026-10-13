package com.gymvip.identity.infrastructure;

import com.gymvip.identity.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT a) FROM Account a JOIN a.roles r WHERE UPPER(r) IN ('ADMIN', 'SUPERADMIN') AND UPPER(a.status) = 'ACTIVO'")
    long countActiveAdmins();
}
