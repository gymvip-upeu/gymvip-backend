package com.gymvip.identity.api;

import com.gymvip.identity.application.port.in.AuthUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request.email() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Correo y contraseña son requeridos"));
        }
        String ip = httpRequest.getRemoteAddr();
        AuthUseCase.LoginResult result = authUseCase.login(request.email().trim(), request.password(), ip);

        return ResponseEntity.ok(Map.of(
                "token", result.token(),
                "id", result.id(),
                "email", result.email(),
                "roles", result.roles()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @RequestHeader(value = "X-Auth-Token", required = false) String customToken) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (customToken != null) {
            token = customToken;
        }
        if (token != null) {
            authUseCase.logout(token);
        }
        return ResponseEntity.noContent().build();
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
}
