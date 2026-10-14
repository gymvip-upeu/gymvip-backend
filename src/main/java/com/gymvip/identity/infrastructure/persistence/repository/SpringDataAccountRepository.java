package com.gymvip.identity.infrastructure.persistence.repository;

import com.gymvip.identity.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(a) FROM AccountJpaEntity a JOIN a.roles r WHERE (r = 'ADMIN' OR r = 'SUPERADMIN') AND UPPER(a.status) = 'ACTIVO'")
    long countActiveAdmins();
}
