package com.gymvip.identity.application;

import com.gymvip.audit.application.AuditService;
import com.gymvip.identity.domain.Account;
import com.gymvip.identity.domain.Session;
import com.gymvip.identity.infrastructure.AccountRepository;
import com.gymvip.identity.infrastructure.SessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(AccountRepository accountRepository,
                       SessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResult login(String email, String rawPassword, String ipAddress) {
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (account == null || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            auditService.recordEvent(email, "LOGIN_FAILED", "Account", null, "Intento de inicio de sesión fallido desde IP: " + ipAddress);
            throw new IllegalArgumentException("Credenciales no válidas");
        }

        if (!"ACTIVO".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalStateException("La cuenta se encuentra inactiva o suspendida");
        }

        // Generate session token valid for 8 hours
        String token = "vip_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(8);
        Session session = new Session(token, account.getId(), expiresAt);
        sessionRepository.save(session);

        auditService.recordEvent(account.getEmail(), "LOGIN_SUCCESS", "Account", account.getId().toString(), "Inicio de sesión exitoso");

        return new LoginResult(token, account.getId(), account.getEmail(), account.getRoles(), expiresAt);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteByToken(token.trim());
        }
    }

    @Transactional
    public void revokeSessionsForAccount(UUID accountId) {
        if (accountId != null) {
            sessionRepository.deleteByAccountId(accountId);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Account> findAccountByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Session> sessionOpt = sessionRepository.findByToken(token.trim());
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        Session session = sessionOpt.get();
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return accountRepository.findById(session.getAccountId())
                .filter(account -> "ACTIVO".equalsIgnoreCase(account.getStatus()));
    }

    public static class LoginResult {
        private final String token;
        private final UUID accountId;
        private final String email;
        private final java.util.Set<String> roles;
        private final LocalDateTime expiresAt;

        public LoginResult(String token, UUID accountId, String email, java.util.Set<String> roles, LocalDateTime expiresAt) {
            this.token = token;
            this.accountId = accountId;
            this.email = email;
            this.roles = roles;
            this.expiresAt = expiresAt;
        }

        public String getToken() { return token; }
        public UUID getAccountId() { return accountId; }
        public String getEmail() { return email; }
        public java.util.Set<String> getRoles() { return roles; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}
