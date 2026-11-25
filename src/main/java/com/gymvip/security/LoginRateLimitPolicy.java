package com.gymvip.security;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Política centralizada de límite de intentos fallidos de inicio de sesión:
 * - Ventana de bloqueo de 5 minutos (300 segundos).
 * - Aislamiento estricto por cuenta/usuario (bloquear a un usuario no bloquea a los demás en la misma IP).
 * - Umbral diferenciado por rol operativo.
 */
public final class LoginRateLimitPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long LOCK_TIME_SECONDS = 300L; // 5 minutos
    public static final long LOCK_TIME_MILLIS = LOCK_TIME_SECONDS * 1000L;

    private static final Map<String, Integer> ROLE_MAX_ATTEMPTS = Map.of(
            "RECEPCION", 3,
            "AUDITOR", 4,
            "GERENTE", 5,
            "CLIENTE", 6,
            "ADMIN", 10
    );

    private LoginRateLimitPolicy() {
    }

    public static int resolveMaxAttempts(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        int resolved = -1;
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring(5);
            }
            Integer limit = ROLE_MAX_ATTEMPTS.get(normalized);
            if (limit != null && limit > resolved) {
                resolved = limit;
            }
        }
        return resolved > 0 ? resolved : DEFAULT_MAX_ATTEMPTS;
    }

    public static String resolveAttemptKey(String ipAddress, String identifier) {
        if (identifier != null && !identifier.isBlank()) {
            return "user:" + identifier.trim().toLowerCase(Locale.ROOT);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            return "ip:" + ipAddress.trim();
        }
        return "UNKNOWN";
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
