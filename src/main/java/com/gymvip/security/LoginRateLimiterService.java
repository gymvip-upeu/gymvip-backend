package com.gymvip.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de protección contra ataques de fuerza bruta en inicio de sesión.
 * Limita los intentos fallidos a un máximo de 5 por minuto por IP.
 */
@Service
public class LoginRateLimiterService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_TIME_DURATION_MS = 60_000L; // 1 minuto de bloqueo

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    private static class AttemptInfo {
        int count;
        long lastAttempt;

        AttemptInfo(int count, long lastAttempt) {
            this.count = count;
            this.lastAttempt = lastAttempt;
        }
    }

    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) return false;
        AttemptInfo info = attempts.get(ip);
        if (info == null) return false;

        if (System.currentTimeMillis() - info.lastAttempt > LOCK_TIME_DURATION_MS) {
            attempts.remove(ip);
            return false;
        }
        return info.count >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String ip) {
        if (ip == null || ip.isBlank()) return;
        attempts.compute(ip, (k, v) -> {
            long now = System.currentTimeMillis();
            if (v == null || (now - v.lastAttempt > LOCK_TIME_DURATION_MS)) {
                return new AttemptInfo(1, now);
            }
            return new AttemptInfo(v.count + 1, now);
        });
    }

    public void recordSuccessfulAttempt(String ip) {
        if (ip != null) {
            attempts.remove(ip);
        }
    }

    public void resetAll() {
        attempts.clear();
    }
}
