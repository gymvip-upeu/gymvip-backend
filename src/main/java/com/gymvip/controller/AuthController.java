package com.gymvip.controller;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.exception.TooManyRequestsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.LoginRateLimitPolicy;
import com.gymvip.security.LoginRateLimiterService;
import com.gymvip.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiterService rateLimiterService;
    private final AccountRepository accountRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(AuthService authService, LoginRateLimiterService rateLimiterService, AccountRepository accountRepository) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
        this.accountRepository = accountRepository;
    }

    public AuthController(AuthService authService, LoginRateLimiterService rateLimiterService) {
        this(authService, rateLimiterService, null);
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String identifier = request.email() != null ? request.email().trim().toLowerCase(java.util.Locale.ROOT) : "";
        String attemptKey = rateLimiterService.resolveAttemptKey(ip, identifier);
        if (rateLimiterService.isBlocked(attemptKey)) {
            long retryAfter = Math.max(1L, rateLimiterService.getRetryAfterSeconds(attemptKey));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(Map.of(
                            "status", 429,
                            "error", "TOO_MANY_REQUESTS",
                            "message", "Demasiados intentos fallidos. Por seguridad, intente nuevamente en 5 minutos."
                    ));
        }

        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.login(request, ip, userAgent);
            rateLimiterService.recordSuccessfulAttempt(attemptKey);
            return ResponseEntity.ok(response);
        } catch (TooManyRequestsException ex) {
            throw ex;
        } catch (Exception ex) {
            int maxAttempts = resolveMaxAttempts(identifier);
            rateLimiterService.recordFailedAttempt(attemptKey, maxAttempts);
            throw ex;
        }
    }

    private int resolveMaxAttempts(String identifier) {
        if (accountRepository == null || identifier == null || identifier.isBlank()) {
            return LoginRateLimitPolicy.DEFAULT_MAX_ATTEMPTS;
        }
        return accountRepository.findByIdentifierIgnoreCase(identifier)
                .map(Account::getRoles)
                .map(LoginRateLimitPolicy::resolveMaxAttempts)
                .orElse(LoginRateLimitPolicy.DEFAULT_MAX_ATTEMPTS);
    }

    public record MfaLoginVerifyRequest(@NotBlank String challengeToken, @NotBlank String code) {}

    @PostMapping(value = "/mfa/verify-login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> verifyMfaLogin(
            @Valid @RequestBody MfaLoginVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.verifyMfaLogin(request.challengeToken(), request.code(), ip, userAgent));
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

    public record OwnProfileUpdateRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String firstName,
            @NotBlank(message = "El apellido es obligatorio") @Size(max = 120) String lastName,
            @Size(max = 30) String phone
    ) {}

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        if (accountRepository == null) {
            return ResponseEntity.ok(Map.of(
                    "email", authentication.getName(),
                    "roles", authentication.getAuthorities()
            ));
        }
        var account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No se encontró el perfil de la cuenta."));
        }
        return ResponseEntity.ok(buildProfilePayload(account));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody OwnProfileUpdateRequest request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        if (accountRepository == null) {
            return ResponseEntity.status(501).body(Map.of("message", "Repositorio de cuentas no disponible."));
        }
        var account = accountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (account == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No se encontró el perfil de la cuenta."));
        }
        String normalizedPhone = (request.phone() == null || request.phone().isBlank()) ? null : request.phone().trim();
        if (normalizedPhone != null && !normalizedPhone.matches("^[0-9+\\-()\\s]{7,20}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono debe tener un formato válido (7 a 20 caracteres)."));
        }
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setPhone(normalizedPhone);
        account.setUpdatedAt(LocalDateTime.now());
        var saved = accountRepository.save(account);
        return ResponseEntity.ok(buildProfilePayload(saved));
    }

    private Map<String, Object> buildProfilePayload(com.gymvip.entity.Account account) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", account.getId());
        profile.put("email", account.getEmail());
        profile.put("firstName", account.getFirstName());
        profile.put("lastName", account.getLastName());
        profile.put("phone", account.getPhone());
        profile.put("roles", account.getRoles());
        profile.put("permissions", com.gymvip.security.PermissionCatalog.resolvePermissions(account.getRoles()));
        profile.put("organizationId", account.getOrganizationId());
        profile.put("primaryBranchId", account.getPrimaryBranchId());
        profile.put("status", account.getStatus());
        profile.put("mfaEnabled", account.isMfaEnabled());
        profile.put("mfaEnrollmentRequired", com.gymvip.security.PermissionCatalog.isSuperAdmin(account.getRoles()) && !account.isMfaEnabled());
        profile.put("directoryOnly", account.isDirectoryOnly());
        profile.put("firstAccessPasswordRequired", account.isFirstAccessPassword() && account.isMustChangePassword());
        profile.put("createdAt", account.getCreatedAt());
        profile.put("updatedAt", account.getUpdatedAt());
        return profile;
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
