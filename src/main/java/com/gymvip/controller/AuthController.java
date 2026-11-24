package com.gymvip.controller;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.security.LoginRateLimiterService;
import com.gymvip.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiterService rateLimiterService;

    public AuthController(AuthService authService, LoginRateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        if (rateLimiterService.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "status", 429,
                    "error", "TOO_MANY_REQUESTS",
                    "message", "Demasiados intentos fallidos. Por seguridad, intente nuevamente en 1 minuto."
            ));
        }

        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.login(request, ip, userAgent);
            rateLimiterService.recordSuccessfulAttempt(ip);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            rateLimiterService.recordFailedAttempt(ip);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Auth-Token", required = false) String customToken
    ) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (customToken != null) {
            token = customToken;
        }
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke-other-sessions")
    public ResponseEntity<Map<String, Object>> revokeOtherSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Auth-Token", required = false) String customToken
    ) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (customToken != null) {
            token = customToken;
        }
        if (token != null) {
            authService.revokeOtherSessions(token);
        }
        return ResponseEntity.ok(Map.of("message", "Otras sesiones cerradas exitosamente"));
    }

    @PostMapping("/revoke-all-sessions")
    public ResponseEntity<Map<String, Object>> revokeAllSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        authService.revokeAllSessions(authHeader.substring(7));
        return ResponseEntity.ok(Map.of("message", "Todas las sesiones y dispositivos de notificación fueron revocados"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        return ResponseEntity.ok(Map.of(
                "email", authentication.getName(),
                "roles", authentication.getAuthorities()
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
