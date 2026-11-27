package com.gymvip.service.impl;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.exception.TooManyRequestsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.LoginAttemptService;
import com.gymvip.security.LoginRateLimitPolicy;
import com.gymvip.security.TotpService;
import com.gymvip.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    public static final String GENERIC_AUTH_ERROR = "Credenciales de acceso incorrectas o cuenta inhabilitada";
    public static final String RATE_LIMIT_ERROR = "Demasiados intentos fallidos de inicio de sesión. Por favor, intente nuevamente en 5 minutos.";

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private TotpService totpService;

    public AuthServiceImpl(AccountRepository accountRepository,
                           SessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService,
                           JwtService jwtService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String identifier = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : "";
        String attemptKey = LoginRateLimitPolicy.resolveAttemptKey(ipAddress, identifier);

        if (loginAttemptService.isBlocked(attemptKey)) {
            throw new TooManyRequestsException(RATE_LIMIT_ERROR, Math.max(1L, loginAttemptService.getRetryAfterSeconds(attemptKey)));
        }

        Account account = accountRepository.findByIdentifierIgnoreCase(identifier).orElse(null);
        int maxAttempts = LoginRateLimitPolicy.resolveMaxAttempts(account != null ? account.getRoles() : null);
        if (loginAttemptService.isBlocked(attemptKey, maxAttempts)) {
            throw new TooManyRequestsException(RATE_LIMIT_ERROR, Math.max(1L, loginAttemptService.getRetryAfterSeconds(attemptKey)));
        }

        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            loginAttemptService.loginFailed(attemptKey, maxAttempts);
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        boolean pending = account.isMustChangePassword()
                && account.getRoles().contains("CLIENTE")
                && "PENDIENTE_ACTIVACION".equals(account.getStatus());
        if (!("ACTIVO".equalsIgnoreCase(account.getStatus()) || pending)) {
            loginAttemptService.loginFailed(attemptKey, maxAttempts);
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        LocalDateTime rawTempExpiresAt = resolveRawTemporaryPasswordExpiresAt(account);
        if (account.isMustChangePassword() && rawTempExpiresAt != null &&
                !rawTempExpiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            loginAttemptService.loginFailed(attemptKey, maxAttempts);
            throw new InvalidCredentialsException("La contraseña temporal venció. Solicita una nueva invitación a Recepción.");
        }

        if (account.isFirstAccessPassword() &&
                accountRepository.consumeFirstAccessPassword(account.getId(), account.getPasswordHash(),
                        passwordEncoder.encode(UUID.randomUUID().toString())) != 1) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }
        loginAttemptService.loginSucceeded(attemptKey);

        // TEC-09: Exigir segundo factor TOTP antes de emitir sesión JWT cuando MFA está habilitado
        if (account.isMfaEnabled() && account.getTotpSecret() != null && !account.getTotpSecret().isBlank() && totpService != null) {
            String challengeToken = jwtService.generateMfaChallengeToken(account);
            return new AuthResponse(
                    null,
                    account.getId(),
                    account.getEmail(),
                    account.getRoles(),
                    true,
                    challengeToken,
                    "Verificación MFA TOTP requerida para completar el ingreso."
            );
        }

        return issueSessionResponse(account, ipAddress, userAgent, rawTempExpiresAt);
    }

    @Override
    @Transactional
    public AuthResponse verifyMfaLogin(String challengeToken, String code, String ipAddress, String userAgent) {
        UUID accountId = jwtService.validateMfaChallengeToken(challengeToken)
                .orElseThrow(() -> new InvalidCredentialsException("El desafío MFA expiró o es inválido. Vuelva a iniciar sesión."));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_AUTH_ERROR));

        String attemptKey = LoginRateLimitPolicy.resolveAttemptKey(ipAddress, account.getEmail().toLowerCase(Locale.ROOT) + ":mfa");
        int maxAttempts = LoginRateLimitPolicy.resolveMaxAttempts(account.getRoles());
        if (loginAttemptService.isBlocked(attemptKey, maxAttempts)) {
            throw new TooManyRequestsException(RATE_LIMIT_ERROR, Math.max(1L, loginAttemptService.getRetryAfterSeconds(attemptKey)));
        }

        if (!"ACTIVO".equalsIgnoreCase(account.getStatus()) || !account.isMfaEnabled() || totpService == null) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        boolean validOtp = totpService.verifyCode(account.getTotpSecret(), code);
        boolean validRecovery = false;
        if (!validOtp && code != null && account.getMfaRecoveryHashes() != null && !account.getMfaRecoveryHashes().isBlank()) {
            String hashed = totpService.hashRecoveryCode(code);
            List<String> current = new ArrayList<>(Arrays.asList(account.getMfaRecoveryHashes().split(",")));
            if (current.remove(hashed)) {
                validRecovery = true;
                account.setMfaRecoveryHashes(String.join(",", current));
                accountRepository.save(account);
            }
        }

        if (!validOtp && !validRecovery) {
            loginAttemptService.loginFailed(attemptKey, maxAttempts);
            throw new InvalidCredentialsException("Código TOTP o código de recuperación inválido.");
        }

        loginAttemptService.loginSucceeded(attemptKey);
        return issueSessionResponse(account, ipAddress, userAgent, resolveRawTemporaryPasswordExpiresAt(account));
    }

    private AuthResponse issueSessionResponse(Account account, String ipAddress, String userAgent, LocalDateTime rawTempExpiresAt) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(jwtService.getExpirationHours());
        if (account.isMustChangePassword() && rawTempExpiresAt != null) {
            var remaining = java.time.Duration.between(
                    LocalDateTime.now(ZoneOffset.UTC),
                    rawTempExpiresAt
            );
            var temporaryLimit = LocalDateTime.now().plus(remaining);
            if (temporaryLimit.isBefore(expiresAt)) {
                expiresAt = temporaryLimit;
            }
        }

        Session session = new Session(sessionId, account.getId(), expiresAt, LocalDateTime.now(), userAgent, ipAddress);
        sessionRepository.save(session);

        String jwtToken = jwtService.generateToken(account, sessionId, expiresAt);
        return new AuthResponse(jwtToken, account.getId(), account.getEmail(), account.getRoles(), false, null, null,
                com.gymvip.security.PermissionCatalog.isSuperAdmin(account.getRoles()) && !account.isMfaEnabled(),
                account.isFirstAccessPassword(), account.isDirectoryOnly());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            String trimmed = token.trim();
            String sessionIdToDelete = jwtService.extractSessionIdIfValidSignature(trimmed)
                    .orElse(trimmed);
            sessionRepository.deleteById(sessionIdToDelete);
        }
    }

    @Override
    @Transactional
    public void revokeOtherSessions(String token) {
        if (token != null && !token.isBlank()) {
            String trimmed = token.trim();
            String currentSessionId = jwtService.extractSessionIdIfValidSignature(trimmed)
                    .orElse(trimmed);
            Session currentSession = sessionRepository.findById(currentSessionId).orElse(null);
            if (currentSession != null) {
                sessionRepository.deleteByAccountIdAndTokenNot(currentSession.getAccountId(), currentSessionId);
            }
        }
    }

    @Override
    @Transactional
    public void revokeAllSessions(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }
        String sessionId = jwtService.extractSessionIdIfValidSignature(token.trim())
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_AUTH_ERROR));
        Session current = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_AUTH_ERROR));
        sessionRepository.deactivateMemberDevices(current.getAccountId());
        sessionRepository.deleteByAccountId(current.getAccountId());
    }

    private LocalDateTime resolveRawTemporaryPasswordExpiresAt(Account account) {
        if (jdbcTemplate != null && account.getId() != null) {
            try {
                LocalDateTime raw = jdbcTemplate.query(
                        "SELECT temporary_password_expires_at FROM accounts WHERE id = ?",
                        rs -> rs.next() ? rs.getObject(1, LocalDateTime.class) : null,
                        account.getId()
                );
                if (raw != null) {
                    return raw;
                }
            } catch (Exception ignored) {
                // Fallback a entidad JPA en entornos de mock aislados
            }
        }
        return account.getTemporaryPasswordExpiresAt();
    }
}
