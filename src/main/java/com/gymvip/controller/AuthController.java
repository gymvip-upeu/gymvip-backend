package com.gymvip.controller;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.service.AuthService;
import com.gymvip.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountRepository accountRepository;

    public AuthController(AuthService authService, AccountRepository accountRepository) {
        this.authService = authService;
        this.accountRepository = accountRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ip, userAgent);
        return ResponseEntity.ok(response);
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
        var account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No se encontró el perfil de la cuenta."));
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", account.getId());
        profile.put("email", account.getEmail());
        profile.put("firstName", account.getFirstName());
        profile.put("lastName", account.getLastName());
        profile.put("phone", account.getPhone());
        profile.put("roles", account.getRoles());
        profile.put("status", account.getStatus());
        profile.put("mfaEnabled", account.isMfaEnabled());
        profile.put("createdAt", account.getCreatedAt());
        profile.put("updatedAt", account.getUpdatedAt());
        return ResponseEntity.ok(profile);
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
