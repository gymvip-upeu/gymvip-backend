package com.gymvip.service.impl;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.exception.TooManyRequestsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.LoginAttemptService;
import com.gymvip.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    public static final String GENERIC_AUTH_ERROR = "Credenciales de acceso incorrectas o cuenta inhabilitada";
    public static final String RATE_LIMIT_ERROR = "Demasiados intentos fallidos de inicio de sesión. Por favor, intente nuevamente en 15 minutos.";

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(AccountRepository accountRepository,
                           SessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String clientKey = resolveClientKey(ipAddress);

        if (loginAttemptService.isBlocked(clientKey)) {
            throw new TooManyRequestsException(RATE_LIMIT_ERROR);
        }

        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        Account account = accountRepository.findByEmailIgnoreCase(email).orElse(null);

        if (account == null) {
            loginAttemptService.loginFailed(clientKey);
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            loginAttemptService.loginFailed(clientKey);
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        if (!"ACTIVO".equalsIgnoreCase(account.getStatus())) {
            loginAttemptService.loginFailed(clientKey);
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        // Inicio de sesión exitoso: limpiar historial de intentos fallidos
        loginAttemptService.loginSucceeded(clientKey);

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        Session session = new Session(token, account.getId(), expiresAt, LocalDateTime.now(), userAgent, ipAddress);
        sessionRepository.save(session);

        return new AuthResponse(token, account.getId(), account.getEmail(), account.getRoles());
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteById(token.trim());
        }
    }

    private String resolveClientKey(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "UNKNOWN";
        }
        return ipAddress.trim();
    }
}
