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
 * - Ventana de bloqueo de 5 minutos (300 s).
 * - Aislamiento por usuario/cuenta (un usuario bloqueado no bloquea a los demás roles o cuentas).
 * - Límite diferenciado según rol (RECEPCION=3, AUDITOR=4, GERENTE=5, CLIENTE=6, ADMIN=10).
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = LoginRateLimitPolicy.DEFAULT_MAX_ATTEMPTS;
    public static final long LOCK_TIME_SECONDS = LoginRateLimitPolicy.LOCK_TIME_SECONDS; // 5 minutos

    public static class AttemptRecord {
        private final int attempts;
        private final int maxAttempts;
        private final Instant lastAttempt;

        public AttemptRecord(int attempts, Instant lastAttempt) {
            this(attempts, MAX_ATTEMPTS, lastAttempt);
        }

        public AttemptRecord(int attempts, int maxAttempts, Instant lastAttempt) {
            this.attempts = attempts;
            this.maxAttempts = maxAttempts;
            this.lastAttempt = lastAttempt;
        }

        public int getAttempts() {
            return attempts;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public Instant getLastAttempt() {
            return lastAttempt;
        }
    }

    private final ConcurrentMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    public void loginFailed(String key) {
        loginFailed(key, MAX_ATTEMPTS);
    }

    public void loginFailed(String key, int maxAttempts) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return;
        }
        int effectiveLimit = maxAttempts > 0 ? maxAttempts : MAX_ATTEMPTS;
        Instant now = Instant.now();
        attemptsCache.compute(normalizedKey, (k, record) -> {
            if (record == null || Duration.between(record.getLastAttempt(), now).getSeconds() >= LOCK_TIME_SECONDS) {
                return new AttemptRecord(1, effectiveLimit, now);
            }
            if (record.getAttempts() >= effectiveLimit) {
                return new AttemptRecord(record.getAttempts(), effectiveLimit, record.getLastAttempt());
            }
            return new AttemptRecord(record.getAttempts() + 1, effectiveLimit, now);
        });
    }

    public void loginSucceeded(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (!normalizedKey.isEmpty()) {
            attemptsCache.remove(normalizedKey);
        }
    }

    public boolean isBlocked(String key) {
        return isBlocked(key, -1);
    }

    public boolean isBlocked(String key, int maxAttemptsOverride) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        AttemptRecord record = attemptsCache.get(normalizedKey);
        if (record == null) {
            return false;
        }
        Instant now = Instant.now();
        long elapsedSeconds = Duration.between(record.getLastAttempt(), now).getSeconds();
        if (elapsedSeconds >= LOCK_TIME_SECONDS) {
            attemptsCache.remove(normalizedKey);
            return false;
        }
        int effectiveLimit = maxAttemptsOverride > 0 ? maxAttemptsOverride : record.getMaxAttempts();
        return record.getAttempts() >= effectiveLimit;
    }

    public long getRetryAfterSeconds(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return LOCK_TIME_SECONDS;
        }
        AttemptRecord record = attemptsCache.get(normalizedKey);
        if (record == null) {
            return 0;
        }
        long elapsedSeconds = Duration.between(record.getLastAttempt(), Instant.now()).getSeconds();
        long remaining = LOCK_TIME_SECONDS - elapsedSeconds;
        return remaining > 0 ? remaining : 0;
    }

    public int getAttempts(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return 0;
        }
        AttemptRecord record = attemptsCache.get(normalizedKey);
        if (record == null) {
            return 0;
        }
        if (Duration.between(record.getLastAttempt(), Instant.now()).getSeconds() >= LOCK_TIME_SECONDS) {
            attemptsCache.remove(normalizedKey);
            return 0;
        }
        return record.getAttempts();
    }

    public void reset(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (!normalizedKey.isEmpty()) {
            attemptsCache.remove(normalizedKey);
        }
    }

    public void clearAll() {
        attemptsCache.clear();
    }
}
