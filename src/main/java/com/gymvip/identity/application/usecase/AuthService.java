package com.gymvip.identity.application.usecase;

import com.gymvip.identity.application.port.in.AuthUseCase;
import com.gymvip.identity.application.port.out.AccountPersistencePort;
import com.gymvip.identity.application.port.out.SessionPersistencePort;
import com.gymvip.identity.domain.exception.InvalidCredentialsException;
import com.gymvip.identity.domain.model.Account;
import com.gymvip.identity.domain.model.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService implements AuthUseCase {

    private final AccountPersistencePort accountPersistencePort;
    private final SessionPersistencePort sessionPersistencePort;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AccountPersistencePort accountPersistencePort,
                       SessionPersistencePort sessionPersistencePort,
                       PasswordEncoder passwordEncoder) {
        this.accountPersistencePort = accountPersistencePort;
        this.sessionPersistencePort = sessionPersistencePort;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResult login(String email, String password, String ipAddress) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new InvalidCredentialsException("Credenciales inválidas: correo y contraseña requeridos.");
        }

        Account account = accountPersistencePort.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas."));

        if (!account.isActive()) {
            throw new InvalidCredentialsException("La cuenta se encuentra inactiva. Contacte al administrador.");
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas.");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);

        Session session = new Session(token, account.getId(), expiresAt, LocalDateTime.now(), "GYMVIP_CLIENT", ipAddress);
        sessionPersistencePort.save(session);

        return new LoginResult(token, account.getId(), account.getEmail(), account.getRoles());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionPersistencePort.deleteByToken(token);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Account getCurrentUser(String token) {
        return findAccountByToken(token).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findAccountByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        Session session = sessionPersistencePort.findByToken(token).orElse(null);
        if (session == null || session.isExpired(LocalDateTime.now())) {
            return Optional.empty();
        }

        return accountPersistencePort.findById(session.getAccountId())
                .filter(Account::isActive);
    }

    @Override
    @Transactional
    public void revokeSessionsForAccount(UUID accountId) {
        sessionPersistencePort.deleteByAccountId(accountId);
    }
}
