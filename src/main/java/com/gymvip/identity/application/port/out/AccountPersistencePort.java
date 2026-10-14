package com.gymvip.identity.application.port.out;

import com.gymvip.identity.domain.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountPersistencePort {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Account> findAll();

    long countActiveAdmins();
}
