package com.gymvip.security;

import com.gymvip.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias — LoginRateLimiterService y Política por Rol")
class LoginRateLimiterUnitTest {

    private LoginRateLimiterService limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiterService();
        limiter.resetAll();
    }

    @Test
    @DisplayName("Duración de bloqueo fijada en 5 minutos (300 segundos)")
    void lockDurationIsFiveMinutes() {
        assertEquals(300_000L, LoginRateLimiterService.LOCK_TIME_DURATION_MS);
        assertEquals(300L, LoginRateLimitPolicy.LOCK_TIME_SECONDS);
    }

    @Test
    @DisplayName("Permite hasta 4 intentos fallidos por defecto sin bloquear")
    void permitsUnderThreshold() {
        String key = "192.168.1.100";
        for (int i = 0; i < 4; i++) {
            assertFalse(limiter.isBlocked(key));
            limiter.recordFailedAttempt(key);
        }
        assertFalse(limiter.isBlocked(key));
    }

    @Test
    @DisplayName("Bloquea al alcanzar el umbral por defecto (5) y expone Retry-After <= 300s")
    void blocksAtThreshold() {
        String key = "192.168.1.105";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt(key);
        }
        assertTrue(limiter.isBlocked(key));
        long retryAfter = limiter.getRetryAfterSeconds(key);
        assertTrue(retryAfter > 0 && retryAfter <= 300L);
    }

    @Test
    @DisplayName("Restablece el contador al registrar un intento exitoso")
    void resetsOnSuccess() {
        String key = "192.168.1.110";
        for (int i = 0; i < 4; i++) {
            limiter.recordFailedAttempt(key);
        }
        limiter.recordSuccessfulAttempt(key);
        assertFalse(limiter.isBlocked(key));
        assertEquals(0, limiter.getAttempts(key));
    }

    @Test
    @DisplayName("Diferencia los límites por rol: RECEPCION=3, AUDITOR=4, GERENTE=5, CLIENTE=6, ADMIN=10")
    void resolvesMaxAttemptsByRole() {
        assertEquals(3, LoginRateLimitPolicy.resolveMaxAttempts(Set.of(Role.RECEPCION.name())));
        assertEquals(4, LoginRateLimitPolicy.resolveMaxAttempts(Set.of(Role.AUDITOR.name())));
        assertEquals(5, LoginRateLimitPolicy.resolveMaxAttempts(Set.of(Role.GERENTE.name())));
        assertEquals(6, LoginRateLimitPolicy.resolveMaxAttempts(Set.of(Role.CLIENTE.name())));
        assertEquals(10, LoginRateLimitPolicy.resolveMaxAttempts(Set.of(Role.ADMIN.name())));
        assertEquals(5, LoginRateLimitPolicy.resolveMaxAttempts(Set.of()));
    }

    @Test
    @DisplayName("Bloquear a un usuario en una IP no bloquea a otros usuarios o roles en la misma IP")
    void isolatesUsersSharingSameIp() {
        String sameIp = "190.12.34.56";
        String recepcionKey = limiter.resolveAttemptKey(sameIp, "recepcion@vipcenterfit.pe");
        String adminKey = limiter.resolveAttemptKey(sameIp, "admin@vipcenterfit.pe");
        String clienteKey = limiter.resolveAttemptKey(sameIp, "cliente@vipcenterfit.pe");

        for (int i = 0; i < 3; i++) {
            limiter.recordFailedAttempt(recepcionKey, LoginRateLimitPolicy.resolveMaxAttempts(Set.of("RECEPCION")));
        }

        assertTrue(limiter.isBlocked(recepcionKey));
        assertFalse(limiter.isBlocked(adminKey));
        assertFalse(limiter.isBlocked(clienteKey));
    }
}
