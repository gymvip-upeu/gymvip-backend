package com.gymvip.security;

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
import com.gymvip.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private CommsServiceClient commsServiceClient;

    private PasswordResetServiceImpl passwordResetService;

    private Account sampleAccount;
    private UUID sampleAccountId;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetServiceImpl(
                accountRepository,
                sessionRepository,
                tokenRepository,
                passwordEncoder,
                auditService,
                commsServiceClient,
                "https://gymvip.pe/reset-password"
        );

        sampleAccountId = UUID.randomUUID();
        sampleAccount = new Account(
                sampleAccountId,
                "socio@vipcenterfit.pe",
                "$2a$10$oldHash123",
                Set.of("CLIENTE"),
                "ACTIVO",
                false
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Debe procesar solicitud para cuenta existente, generar token con hash y enviar email")
    void testRequestResetExistingAccount() {
        when(accountRepository.findByEmailIgnoreCase("socio@vipcenterfit.pe")).thenReturn(Optional.of(sampleAccount));

        PasswordResetResponse response = passwordResetService.requestReset(
                new PasswordResetRequest("socio@vipcenterfit.pe"),
                "192.168.1.50"
        );

        assertNotNull(response);
        assertEquals(PasswordResetServiceImpl.GENERIC_RESET_MSG, response.message());

        verify(tokenRepository).invalidateActiveTokensForAccount(sampleAccountId);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(commsServiceClient).sendPasswordResetEmail(eq("socio@vipcenterfit.pe"), contains("https://gymvip.pe/reset-password?token="), anyString(), eq("Socio VIP"));
        verify(auditService).recordEvent(eq("socio@vipcenterfit.pe"), eq("PASSWORD_RESET_REQUESTED"), eq("Account"), eq(sampleAccountId.toString()), anyString());
    }

    @Test
    @DisplayName("Debe responder de forma idéntica sin revelar que el correo no existe (anti-enumeración)")
    void testRequestResetNonExistingAccount() {
        when(accountRepository.findByEmailIgnoreCase("desconocido@vipcenterfit.pe")).thenReturn(Optional.empty());

        PasswordResetResponse response = passwordResetService.requestReset(
                new PasswordResetRequest("desconocido@vipcenterfit.pe"),
                "192.168.1.51"
        );

        assertNotNull(response);
        assertEquals(PasswordResetServiceImpl.GENERIC_RESET_MSG, response.message());
        verify(tokenRepository, never()).save(any());
        verify(commsServiceClient, never()).sendPasswordResetEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Debe confirmar restablecimiento con token válido, actualizar hash y revocar sesiones")
    void testConfirmResetValidToken() {
        String rawToken = "abc1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        String tokenHash = sha256(rawToken);

        PasswordResetToken tokenEntity = new PasswordResetToken(
                UUID.randomUUID(),
                sampleAccountId,
                tokenHash,
                LocalDateTime.now().plusMinutes(10)
        );

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));
        when(accountRepository.findById(sampleAccountId)).thenReturn(Optional.of(sampleAccount));
        when(passwordEncoder.encode("NuevaPassword123")).thenReturn("$2a$10$newHash456");

        PasswordResetConfirmResponse response = passwordResetService.confirmReset(
                new PasswordResetConfirmRequest(rawToken, "NuevaPassword123"),
                "192.168.1.50"
        );

        assertTrue(response.success());
        assertTrue(tokenEntity.isUsed());
        assertNotNull(tokenEntity.getUsedAt());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals("$2a$10$newHash456", accountCaptor.getValue().getPasswordHash());

        verify(sessionRepository).deleteByAccountId(sampleAccountId);
        verify(auditService).recordEvent(eq("socio@vipcenterfit.pe"), eq("PASSWORD_RESET_CONFIRMED"), eq("Account"), eq(sampleAccountId.toString()), anyString());
    }

    @Test
    @DisplayName("Debe rechazar token vencido")
    void testConfirmResetExpiredToken() {
        String rawToken = "expired1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String tokenHash = sha256(rawToken);

        PasswordResetToken tokenEntity = new PasswordResetToken(
                UUID.randomUUID(),
                sampleAccountId,
                tokenHash,
                LocalDateTime.now().minusMinutes(1)
        );

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));

        assertThrows(InvalidCredentialsException.class, () ->
                passwordResetService.confirmReset(new PasswordResetConfirmRequest(rawToken, "NuevaPass123"), "192.168.1.50")
        );

        verify(accountRepository, never()).save(any());
        verify(sessionRepository, never()).deleteByAccountId(any());
    }

    @Test
    @DisplayName("Debe rechazar token ya utilizado")
    void testConfirmResetAlreadyUsedToken() {
        String rawToken = "used1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String tokenHash = sha256(rawToken);

        PasswordResetToken tokenEntity = new PasswordResetToken(
                UUID.randomUUID(),
                sampleAccountId,
                tokenHash,
                LocalDateTime.now().plusMinutes(10)
        );
        tokenEntity.setUsedAt(LocalDateTime.now().minusMinutes(5));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));

        assertThrows(InvalidCredentialsException.class, () ->
                passwordResetService.confirmReset(new PasswordResetConfirmRequest(rawToken, "NuevaPass123"), "192.168.1.50")
        );

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar contraseña débil que no cumple longitud o alfanumérico")
    void testConfirmResetWeakPassword() {
        String rawToken = "validtoken1234567890abcdef1234567890abcdef1234567890abcdef12345678";
        String tokenHash = sha256(rawToken);

        PasswordResetToken tokenEntity = new PasswordResetToken(
                UUID.randomUUID(),
                sampleAccountId,
                tokenHash,
                LocalDateTime.now().plusMinutes(10)
        );

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenEntity));

        // Menos de 8 caracteres
        assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.confirmReset(new PasswordResetConfirmRequest(rawToken, "12345"), "192.168.1.50")
        );

        // Sin números
        assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.confirmReset(new PasswordResetConfirmRequest(rawToken, "sololetras"), "192.168.1.50")
        );
    }

    @Test
    @DisplayName("Debe bloquear exceso de solicitudes de recuperación tras superar límite")
    void testRateLimitResetRequests() {
        when(accountRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(sampleAccount));

        String testIp = "10.0.0.99";
        for (int i = 0; i < 3; i++) {
            assertNotNull(passwordResetService.requestReset(new PasswordResetRequest("socio@vipcenterfit.pe"), testIp));
        }

        assertThrows(TooManyRequestsException.class, () ->
                passwordResetService.requestReset(new PasswordResetRequest("socio@vipcenterfit.pe"), testIp)
        );
    }
}
