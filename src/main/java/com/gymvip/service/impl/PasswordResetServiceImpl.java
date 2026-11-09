package com.gymvip.service.impl;

import com.gymvip.dto.request.PasswordResetConfirmRequest;
import com.gymvip.dto.request.PasswordResetRequest;
import com.gymvip.dto.response.PasswordResetConfirmResponse;
import com.gymvip.dto.response.PasswordResetResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.PasswordResetToken;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.exception.TooManyRequestsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.PasswordResetTokenRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.CommsServiceClient;
import com.gymvip.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    public static final String GENERIC_RESET_MSG = "Si el correo electrónico está registrado, se han enviado las instrucciones para restablecer la contraseña.";

    private final AccountRepository accountRepository;
    private final SessionRepository sessionRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CommsServiceClient commsServiceClient;
    private final String resetBaseUrl;

    // Control de límite de solicitudes por IP/email (máximo 3 peticiones cada 15 minutos)
    private final Map<String, RequestCounter> rateLimitMap = new ConcurrentHashMap<>();

    private static class RequestCounter {
        int count;
        long resetTimeMillis;
    }

    public PasswordResetServiceImpl(
            AccountRepository accountRepository,
            SessionRepository sessionRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            CommsServiceClient commsServiceClient,
            @Value("${app.password-reset.base-url:https://gymvip.pe/reset-password}") String resetBaseUrl
    ) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.commsServiceClient = commsServiceClient;
        this.resetBaseUrl = resetBaseUrl;
    }

    @Override
    @Transactional
    public PasswordResetResponse requestReset(PasswordResetRequest request, String ipAddress) {
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        String rateKey = (ipAddress != null ? ipAddress : "unknown") + ":" + email;

        checkRateLimit(rateKey);

        Account account = accountRepository.findByEmailIgnoreCase(email).orElse(null);

        // Anti-enumeración: si no existe o está inactivo, responder con mensaje neutro sin error
        if (account == null || !"ACTIVO".equalsIgnoreCase(account.getStatus())) {
            log.info("[PASSWORD RESET] Solicitud para correo no existente o inactivo (respuesta neutra)");
            return new PasswordResetResponse(GENERIC_RESET_MSG);
        }

        // Generar token aleatorio criptográficamente seguro (32 bytes hex = 64 caracteres)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        StringBuilder rawTokenBuilder = new StringBuilder();
        for (byte b : randomBytes) {
            rawTokenBuilder.append(String.format("%02x", b));
        }
        String rawToken = rawTokenBuilder.toString();
        String tokenHash = sha256Hex(rawToken);

        // Invalidar tokens anteriores activos de la cuenta
        tokenRepository.invalidateActiveTokensForAccount(account.getId());

        // Almacenar únicamente el hash del token con TTL de 15 minutos
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        PasswordResetToken tokenEntity = new PasswordResetToken(
                UUID.randomUUID(),
                account.getId(),
                tokenHash,
                expiresAt
        );
        tokenRepository.save(tokenEntity);

        // Construir enlace de recuperación
        String resetLink = resetBaseUrl + "?token=" + rawToken;

        // Despachar email vía microservicio de comunicaciones
        commsServiceClient.sendPasswordResetEmail(account.getEmail(), resetLink, rawToken, "Socio VIP");

        auditService.recordEvent(
                account.getEmail(),
                "PASSWORD_RESET_REQUESTED",
                "Account",
                account.getId().toString(),
                "Solicitud de restablecimiento de contraseña generada (TTL 15 min)"
        );

        return new PasswordResetResponse(GENERIC_RESET_MSG);
    }

    @Override
    @Transactional
    public PasswordResetConfirmResponse confirmReset(PasswordResetConfirmRequest request, String ipAddress) {
        String rawToken = request.token() != null ? request.token().trim() : "";
        if (rawToken.isBlank()) {
            throw new InvalidCredentialsException("El token de recuperación es inválido o ha expirado.");
        }

        String tokenHash = sha256Hex(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("El token de recuperación es inválido o ha expirado."));

        if (!token.isValid()) {
            throw new InvalidCredentialsException("El token de recuperación es inválido o ha expirado.");
        }

        String newPassword = request.newPassword() != null ? request.newPassword() : "";
        validatePasswordStrength(newPassword);

        Account account = accountRepository.findById(token.getAccountId())
                .orElseThrow(() -> new InvalidCredentialsException("La cuenta asociada al token no fue encontrada."));

        // Actualizar contraseña de la cuenta
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        // Consumir el token (un solo uso)
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Invalidar de inmediato todas las sesiones anteriores activas
        sessionRepository.deleteByAccountId(account.getId());

        auditService.recordEvent(
                account.getEmail(),
                "PASSWORD_RESET_CONFIRMED",
                "Account",
                account.getId().toString(),
                "Contraseña restablecida exitosamente. Sesiones previas revocadas."
        );

        return new PasswordResetConfirmResponse(
                true,
                "Contraseña actualizada exitosamente. Inicie sesión con sus nuevas credenciales."
        );
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        if (!hasDigit || !hasLetter) {
            throw new IllegalArgumentException("La contraseña debe ser alfanumérica y contener al menos una letra y un número.");
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Error al calcular hash SHA-256", e);
        }
    }

    private synchronized void checkRateLimit(String key) {
        long now = System.currentTimeMillis();
        RequestCounter counter = rateLimitMap.get(key);
        if (counter == null || now > counter.resetTimeMillis) {
            counter = new RequestCounter();
            counter.count = 1;
            counter.resetTimeMillis = now + 15 * 60 * 1000L;
            rateLimitMap.put(key, counter);
            return;
        }

        if (counter.count >= 3) {
            throw new TooManyRequestsException("Demasiadas solicitudes de recuperación. Por favor intente nuevamente en 15 minutos.");
        }

        counter.count++;
    }
}
