package com.gymvip.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de protección contra ataques de fuerza bruta en inicio de sesión.
 * Bloqueo de 5 minutos aislado por usuario/cuenta con umbral según rol.
 */
@Service
public class LoginRateLimiterService {

    public static final int MAX_ATTEMPTS = LoginRateLimitPolicy.DEFAULT_MAX_ATTEMPTS;
    public static final long LOCK_TIME_DURATION_MS = LoginRateLimitPolicy.LOCK_TIME_MILLIS; // 5 minutos

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    private static class AttemptInfo {
        final int count;
        final int maxAttempts;
        final long referenceTimeMillis;

        AttemptInfo(int count, int maxAttempts, long referenceTimeMillis) {
            this.count = count;
            this.maxAttempts = maxAttempts;
            this.referenceTimeMillis = referenceTimeMillis;
        }
    }

    public String resolveAttemptKey(String ip, String identifier) {
        return LoginRateLimitPolicy.resolveAttemptKey(ip, identifier);
    }

    public boolean isBlocked(String key) {
        return isBlocked(key, -1);
    }

    public boolean isBlocked(String key, int maxAttemptsOverride) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return false;
        }
        AttemptInfo info = attempts.get(normalizedKey);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() - info.referenceTimeMillis >= LOCK_TIME_DURATION_MS) {
            attempts.remove(normalizedKey);
            return false;
        }
        int effectiveLimit = maxAttemptsOverride > 0 ? maxAttemptsOverride : info.maxAttempts;
        return info.count >= effectiveLimit;
    }

    public void recordFailedAttempt(String key) {
        recordFailedAttempt(key, MAX_ATTEMPTS);
    }

    public void recordFailedAttempt(String key, int maxAttempts) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return;
        }
        int effectiveLimit = maxAttempts > 0 ? maxAttempts : MAX_ATTEMPTS;
        attempts.compute(normalizedKey, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || (now - existing.referenceTimeMillis >= LOCK_TIME_DURATION_MS)) {
                return new AttemptInfo(1, effectiveLimit, now);
            }
            if (existing.count >= effectiveLimit) {
                // Mantener la hora del bloqueo sin extender indefinidamente la ventana de 5 minutos
                return new AttemptInfo(existing.count, effectiveLimit, existing.referenceTimeMillis);
            }
            return new AttemptInfo(existing.count + 1, effectiveLimit, now);
        });
    }

    public long getRetryAfterSeconds(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return LoginRateLimitPolicy.LOCK_TIME_SECONDS;
        }
        AttemptInfo info = attempts.get(normalizedKey);
        if (info == null) {
            return 0;
        }
        long remainingMs = LOCK_TIME_DURATION_MS - (System.currentTimeMillis() - info.referenceTimeMillis);
        return remainingMs > 0 ? Math.max(1L, (remainingMs + 999L) / 1000L) : 0;
    }

    public int getAttempts(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (normalizedKey.isEmpty()) {
            return 0;
        }
        AttemptInfo info = attempts.get(normalizedKey);
        if (info == null) {
            return 0;
        }
        if (System.currentTimeMillis() - info.referenceTimeMillis >= LOCK_TIME_DURATION_MS) {
            attempts.remove(normalizedKey);
            return 0;
        }
        return info.count;
    }

    public void recordSuccessfulAttempt(String key) {
        String normalizedKey = LoginRateLimitPolicy.normalizeKey(key);
        if (!normalizedKey.isEmpty()) {
            attempts.remove(normalizedKey);
        }
    }

    public void resetAll() {
        attempts.clear();
    }
}
