package com.gymvip.security;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.dto.response.StaffAccountResponse;
import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.enums.Role;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.exception.TooManyRequestsException;
import com.gymvip.mapper.AccountMapper;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.impl.AccountServiceImpl;
import com.gymvip.service.impl.AuthServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginSecurityAndRateLimitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private LoginAttemptService loginAttemptService;
    private AuthServiceImpl authService;
    private AccountServiceImpl accountService;
    private TokenAuthenticationFilter tokenFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        loginAttemptService = new LoginAttemptService();
        authService = new AuthServiceImpl(accountRepository, sessionRepository, passwordEncoder, loginAttemptService);
        accountService = new AccountServiceImpl(accountRepository, sessionRepository, accountMapper, passwordEncoder, auditService);
        tokenFilter = new TokenAuthenticationFilter(sessionRepository, accountRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        loginAttemptService.clearAll();
    }

    @Test
    @DisplayName("Debe bloquear la IP tras 5 intentos fallidos consecutivos y permitir desbloqueo al expirar o resetear")
    void testRateLimitFiveFailedAttemptsBlocksClient() {
        String testIp = "192.168.1.100";
        assertFalse(loginAttemptService.isBlocked(testIp));

        for (int i = 1; i <= 4; i++) {
            loginAttemptService.loginFailed(testIp);
            assertFalse(loginAttemptService.isBlocked(testIp), "No debe bloquear antes de 5 intentos");
            assertEquals(i, loginAttemptService.getAttempts(testIp));
        }

        // Quinto intento fallido
        loginAttemptService.loginFailed(testIp);
        assertTrue(loginAttemptService.isBlocked(testIp), "Debe estar bloqueado tras 5 intentos");
        assertTrue(loginAttemptService.getRetryAfterSeconds(testIp) > 0);

        // Reset exitoso
        loginAttemptService.loginSucceeded(testIp);
        assertFalse(loginAttemptService.isBlocked(testIp));
        assertEquals(0, loginAttemptService.getAttempts(testIp));
    }

    @Test
    @DisplayName("AuthService debe lanzar TooManyRequestsException cuando el cliente está bloqueado")
    void testAuthServiceThrowsTooManyRequestsWhenBlocked() {
        String testIp = "200.48.10.5";
        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed(testIp);
        }

        LoginRequest loginReq = new LoginRequest("admin@gymvip.com", "cualquiera123");
        TooManyRequestsException ex = assertThrows(
                TooManyRequestsException.class,
                () -> authService.login(loginReq, testIp, "Mozilla/5.0")
        );

        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
        verify(accountRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Debe arrojar el mismo mensaje genérico para cuenta inexistente, password erróneo y cuenta inactiva (anti-enumeración)")
    void testGenericErrorMessagePreventsAccountEnumeration() {
        String testIp = "10.0.0.1";
        String expectedMessage = "Credenciales de acceso incorrectas o cuenta inhabilitada";

        // Caso 1: Cuenta no existe
        when(accountRepository.findByEmailIgnoreCase("inexistente@gymvip.com")).thenReturn(Optional.empty());
        InvalidCredentialsException ex1 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("inexistente@gymvip.com", "secret"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex1.getMessage());
        assertEquals(1, loginAttemptService.getAttempts(testIp));

        // Caso 2: Password no coincide
        UUID id = UUID.randomUUID();
        Account activeAccount = new Account(id, "activo@gymvip.com", "hash_real", Set.of("ADMIN"), "ACTIVO", false);
        when(accountRepository.findByEmailIgnoreCase("activo@gymvip.com")).thenReturn(Optional.of(activeAccount));
        when(passwordEncoder.matches("wrong_pass", "hash_real")).thenReturn(false);

        InvalidCredentialsException ex2 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("activo@gymvip.com", "wrong_pass"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex2.getMessage());
        assertEquals(2, loginAttemptService.getAttempts(testIp));

        // Caso 3: Cuenta inactiva con password correcto
        Account inactiveAccount = new Account(UUID.randomUUID(), "inactivo@gymvip.com", "hash_real", Set.of("RECEPCION"), "INACTIVO", false);
        when(accountRepository.findByEmailIgnoreCase("inactivo@gymvip.com")).thenReturn(Optional.of(inactiveAccount));
        when(passwordEncoder.matches("valid_pass", "hash_real")).thenReturn(true);

        InvalidCredentialsException ex3 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("inactivo@gymvip.com", "valid_pass"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex3.getMessage());
        assertEquals(3, loginAttemptService.getAttempts(testIp));
    }

    @Test
    @DisplayName("Login exitoso resetea el contador de intentos fallidos y emite sesión válida")
    void testSuccessfulLoginResetsAttemptsAndCreatesSession() {
        String testIp = "10.0.0.2";
        // Previamente 3 intentos fallidos
        loginAttemptService.loginFailed(testIp);
        loginAttemptService.loginFailed(testIp);
        loginAttemptService.loginFailed(testIp);
        assertEquals(3, loginAttemptService.getAttempts(testIp));

        UUID id = UUID.randomUUID();
        Account activeAccount = new Account(id, "admin@gymvip.com", "hash_correcto", Set.of("ADMIN"), "ACTIVO", false);
        when(accountRepository.findByEmailIgnoreCase("admin@gymvip.com")).thenReturn(Optional.of(activeAccount));
        when(passwordEncoder.matches("clave_correcta", "hash_correcto")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("admin@gymvip.com", "clave_correcta"), testIp, "Browser");

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(0, loginAttemptService.getAttempts(testIp));
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    @DisplayName("TokenFilter debe purgar reactivamente sesiones expiradas y no autenticar")
    void testExpiredSessionIsPurgedAndNotAuthenticated() throws ServletException, IOException {
        String token = "expired_token_123";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        Session expiredSession = new Session(token, UUID.randomUUID(), LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusHours(1), "agent", "127.0.0.1");
        when(sessionRepository.findById(token)).thenReturn(Optional.of(expiredSession));

        tokenFilter.doFilter(request, response, filterChain);

        verify(sessionRepository, times(1)).delete(expiredSession);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("AccountService.updateRole debe revocar inmediatamente todas las sesiones asociadas a la cuenta")
    void testUpdateRoleRevokesExistingSessions() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "staff@gymvip.com", "hash", Set.of("RECEPCION"), "ACTIVO", false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountMapper.toResponse(any(Account.class))).thenReturn(
                new StaffAccountResponse(accountId, "staff@gymvip.com", Set.of("ADMIN"), "ACTIVO", false, LocalDateTime.now(), LocalDateTime.now())
        );

        UpdateStaffRoleRequest request = new UpdateStaffRoleRequest("ADMIN");
        accountService.updateRole(accountId, request, "admin@gymvip.com");

        verify(sessionRepository, times(1)).deleteByAccountId(accountId);
        verify(auditService, times(1)).recordEvent(eq("admin@gymvip.com"), eq("UPDATE_STAFF_ROLE"), eq("Account"), eq(accountId.toString()), anyString());
    }
}

