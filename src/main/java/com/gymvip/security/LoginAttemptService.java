package com.gymvip.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Servicio de control de intentos de inicio de sesión para mitigar ataques
 * de fuerza bruta y credential stuffing.
 * 
 * Política:
 * - Límite de 5 intentos fallidos consecutivos en una ventana de 15 minutos (900 s).
 * - Bloqueo inmediato tras el 5to intento fallido por un periodo de 15 minutos.
 * - Restablecimiento del contador tras un inicio de sesión exitoso.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_TIME_SECONDS = 900; // 15 minutos

    public static class AttemptRecord {
        private int attempts;
        private Instant lastAttempt;

        public AttemptRecord(int attempts, Instant lastAttempt) {
            this.attempts = attempts;
            this.lastAttempt = lastAttempt;
        }

        public int getAttempts() {
            return attempts;
        }

        public Instant getLastAttempt() {
            return lastAttempt;
        }
    }

    private final ConcurrentMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    public void loginFailed(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        attemptsCache.compute(key.trim(), (k, record) -> {
            if (record == null) {
                return new AttemptRecord(1, now);
            }
            // Si la ventana de bloqueo ya expiró, reiniciar el conteo
            if (Duration.between(record.getLastAttempt(), now).getSeconds() >= LOCK_TIME_SECONDS) {
                return new AttemptRecord(1, now);
            }
            return new AttemptRecord(record.getAttempts() + 1, now);
        });
    }

    public void loginSucceeded(String key) {
        if (key != null && !key.isBlank()) {
            attemptsCache.remove(key.trim());
        }
    }

    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        AttemptRecord record = attemptsCache.get(key.trim());
        if (record == null) {
            return false;
        }
        Instant now = Instant.now();
        long elapsedSeconds = Duration.between(record.getLastAttempt(), now).getSeconds();
        if (elapsedSeconds >= LOCK_TIME_SECONDS) {
            attemptsCache.remove(key.trim());
            return false;
        }
        return record.getAttempts() >= MAX_ATTEMPTS;
    }

    public long getRetryAfterSeconds(String key) {
        if (key == null || key.isBlank()) {
            return LOCK_TIME_SECONDS;
        }
        AttemptRecord record = attemptsCache.get(key.trim());
        if (record == null) {
            return 0;
        }
        long elapsedSeconds = Duration.between(record.getLastAttempt(), Instant.now()).getSeconds();
        long remaining = LOCK_TIME_SECONDS - elapsedSeconds;
        return remaining > 0 ? remaining : 0;
    }

    public int getAttempts(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        AttemptRecord record = attemptsCache.get(key.trim());
        if (record == null) {
            return 0;
        }
        if (Duration.between(record.getLastAttempt(), Instant.now()).getSeconds() >= LOCK_TIME_SECONDS) {
            attemptsCache.remove(key.trim());
            return 0;
        }
        return record.getAttempts();
    }

    public void reset(String key) {
        if (key != null) {
            attemptsCache.remove(key.trim());
        }
    }

    public void clearAll() {
        attemptsCache.clear();
    }
}

