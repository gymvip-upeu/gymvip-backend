package com.gymvip.controller;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.exception.GlobalExceptionHandler;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.LoginRateLimiterService;
import com.gymvip.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Pruebas MockMvc — Rate Limit por Usuario/Rol en AuthController")
class AuthControllerRateLimitTest {

    private AuthService authService;
    private LoginRateLimiterService rateLimiterService;
    private AccountRepository accountRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        rateLimiterService = new LoginRateLimiterService();
        accountRepository = Mockito.mock(AccountRepository.class);
        AuthController controller = new AuthController(authService, rateLimiterService, accountRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Bloquea a RECEPCION al 3er fallo por 5 minutos sin bloquear a ADMIN en la misma IP")
    void recepcionLockoutDoesNotBlockAdminOnSameIp() throws Exception {
        Account recepcion = new Account(UUID.randomUUID(), "recepcion@vipcenterfit.pe", "h1", Set.of("RECEPCION"), "ACTIVO", false);
        Account admin = new Account(UUID.randomUUID(), "admin@vipcenterfit.pe", "h2", Set.of("ADMIN"), "ACTIVO", false);

        when(accountRepository.findByIdentifierIgnoreCase("recepcion@vipcenterfit.pe")).thenReturn(Optional.of(recepcion));
        when(accountRepository.findByIdentifierIgnoreCase("admin@vipcenterfit.pe")).thenReturn(Optional.of(admin));

        when(authService.login(any(LoginRequest.class), anyString(), any()))
                .thenAnswer(inv -> {
                    LoginRequest req = inv.getArgument(0);
                    if ("admin@vipcenterfit.pe".equalsIgnoreCase(req.email()) && "ok".equals(req.password())) {
                        return new AuthResponse("jwt-admin", admin.getId(), admin.getEmail(), admin.getRoles());
                    }
                    throw new InvalidCredentialsException("Credenciales de acceso incorrectas o cuenta inhabilitada");
                });

        String recepcionBadJson = "{\"email\":\"recepcion@vipcenterfit.pe\",\"password\":\"wrong\"}";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "181.65.10.1")
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(recepcionBadJson))
                    .andExpect(status().isUnauthorized());
        }

        // 4to intento de recepción bloqueado (429, mensaje 5 minutos)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "181.65.10.1")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recepcionBadJson))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5 minutos")));

        // Admin desde la MISMA IP entra sin bloqueo
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "181.65.10.1")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@vipcenterfit.pe\",\"password\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-admin"));
    }
}
