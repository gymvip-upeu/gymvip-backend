package com.gymvip.identity.application.port.in;

import com.gymvip.identity.domain.model.Account;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AuthUseCase {

    LoginResult login(String email, String password, String ipAddress);

    void logout(String token);

    Account getCurrentUser(String token);

    Optional<Account> findAccountByToken(String token);

    void revokeSessionsForAccount(UUID accountId);

    record LoginResult(
            String token,
            UUID id,
            String email,
            Set<String> roles
    ) {}
}
