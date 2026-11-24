package com.gymvip.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias — LoginRateLimiterService")
class LoginRateLimiterUnitTest {

    private LoginRateLimiterService limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiterService();
        limiter.resetAll();
    }

    @Test
    @DisplayName("Permite hasta 4 intentos fallidos sin bloquear")
    void permitsUnderThreshold() {
        String ip = "192.168.1.100";
        for (int i = 0; i < 4; i++) {
            assertFalse(limiter.isBlocked(ip));
            limiter.recordFailedAttempt(ip);
        }
        assertFalse(limiter.isBlocked(ip));
    }

    @Test
    @DisplayName("Bloquea la IP al alcanzar el 5to intento fallido")
    void blocksAtThreshold() {
        String ip = "192.168.1.105";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt(ip);
        }
        assertTrue(limiter.isBlocked(ip));
    }

    @Test
    @DisplayName("Restablece el contador al registrar un intento exitoso")
    void resetsOnSuccess() {
        String ip = "192.168.1.110";
        for (int i = 0; i < 4; i++) {
            limiter.recordFailedAttempt(ip);
        }
        limiter.recordSuccessfulAttempt(ip);
        assertFalse(limiter.isBlocked(ip));
    }
}
