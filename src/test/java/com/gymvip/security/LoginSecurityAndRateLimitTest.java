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
    private JwtService jwtService;
    private AuthServiceImpl authService;
    private AccountServiceImpl accountService;
    private TokenAuthenticationFilter tokenFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        loginAttemptService = new LoginAttemptService();
        jwtService = new JwtService();
        authService = new AuthServiceImpl(accountRepository, sessionRepository, passwordEncoder, loginAttemptService, jwtService);
        accountService = new AccountServiceImpl(accountRepository, sessionRepository, accountMapper, passwordEncoder, auditService);
        tokenFilter = new TokenAuthenticationFilter(sessionRepository, accountRepository, jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        loginAttemptService.clearAll();
    }

    @Test
    @DisplayName("Debe bloquear la cuenta tras el máximo de intentos fallidos y permitir desbloqueo al resetear")
    void testRateLimitFiveFailedAttemptsBlocksClient() {
        String key = LoginRateLimitPolicy.resolveAttemptKey("192.168.1.100", "gerente@gymvip.com");
        assertFalse(loginAttemptService.isBlocked(key));

        for (int i = 1; i <= 4; i++) {
            loginAttemptService.loginFailed(key);
            assertFalse(loginAttemptService.isBlocked(key), "No debe bloquear antes de 5 intentos");
            assertEquals(i, loginAttemptService.getAttempts(key));
        }

        // Quinto intento fallido
        loginAttemptService.loginFailed(key);
        assertTrue(loginAttemptService.isBlocked(key), "Debe estar bloqueado tras 5 intentos");
        long retryAfter = loginAttemptService.getRetryAfterSeconds(key);
        assertTrue(retryAfter > 0 && retryAfter <= 300L, "El bloqueo debe durar 5 minutos (300 s)");

        // Reset exitoso
        loginAttemptService.loginSucceeded(key);
        assertFalse(loginAttemptService.isBlocked(key));
        assertEquals(0, loginAttemptService.getAttempts(key));
    }

    @Test
    @DisplayName("AuthService debe lanzar TooManyRequestsException (5 minutos) cuando el usuario está bloqueado")
    void testAuthServiceThrowsTooManyRequestsWhenBlocked() {
        String testIp = "200.48.10.5";
        String key = LoginRateLimitPolicy.resolveAttemptKey(testIp, "admin@gymvip.com");
        for (int i = 0; i < 10; i++) {
            loginAttemptService.loginFailed(key, 10);
        }

        LoginRequest loginReq = new LoginRequest("admin@gymvip.com", "cualquiera123");
        TooManyRequestsException ex = assertThrows(
                TooManyRequestsException.class,
                () -> authService.login(loginReq, testIp, "Mozilla/5.0")
        );

        assertTrue(ex.getMessage().contains("5 minutos"));
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 300L);
        verify(accountRepository, never()).findByIdentifierIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Debe arrojar el mismo mensaje genérico para cuenta inexistente, password erróneo y cuenta inactiva (anti-enumeración)")
    void testGenericErrorMessagePreventsAccountEnumeration() {
        String testIp = "10.0.0.1";
        String expectedMessage = "Credenciales de acceso incorrectas o cuenta inhabilitada";

        // Caso 1: Cuenta no existe
        when(accountRepository.findByIdentifierIgnoreCase("inexistente@gymvip.com")).thenReturn(Optional.empty());
        InvalidCredentialsException ex1 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("inexistente@gymvip.com", "secret"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex1.getMessage());
        assertEquals(1, loginAttemptService.getAttempts(LoginRateLimitPolicy.resolveAttemptKey(testIp, "inexistente@gymvip.com")));

        // Caso 2: Password no coincide
        UUID id = UUID.randomUUID();
        Account activeAccount = new Account(id, "activo@gymvip.com", "hash_real", Set.of("ADMIN"), "ACTIVO", false);
        when(accountRepository.findByIdentifierIgnoreCase("activo@gymvip.com")).thenReturn(Optional.of(activeAccount));
        when(passwordEncoder.matches("wrong_pass", "hash_real")).thenReturn(false);

        InvalidCredentialsException ex2 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("activo@gymvip.com", "wrong_pass"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex2.getMessage());
        assertEquals(1, loginAttemptService.getAttempts(LoginRateLimitPolicy.resolveAttemptKey(testIp, "activo@gymvip.com")));

        // Caso 3: Cuenta inactiva con password correcto
        Account inactiveAccount = new Account(UUID.randomUUID(), "inactivo@gymvip.com", "hash_real", Set.of("RECEPCION"), "INACTIVO", false);
        when(accountRepository.findByIdentifierIgnoreCase("inactivo@gymvip.com")).thenReturn(Optional.of(inactiveAccount));
        when(passwordEncoder.matches("valid_pass", "hash_real")).thenReturn(true);

        InvalidCredentialsException ex3 = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("inactivo@gymvip.com", "valid_pass"), testIp, "Agent")
        );
        assertEquals(expectedMessage, ex3.getMessage());
        assertEquals(1, loginAttemptService.getAttempts(LoginRateLimitPolicy.resolveAttemptKey(testIp, "inactivo@gymvip.com")));
    }

    @Test
    @DisplayName("Login exitoso resetea el contador de intentos fallidos y emite sesión válida")
    void testSuccessfulLoginResetsAttemptsAndCreatesSession() {
        String testIp = "10.0.0.2";
        String adminKey = LoginRateLimitPolicy.resolveAttemptKey(testIp, "admin@gymvip.com");
        // Previamente 3 intentos fallidos
        loginAttemptService.loginFailed(adminKey, 10);
        loginAttemptService.loginFailed(adminKey, 10);
        loginAttemptService.loginFailed(adminKey, 10);
        assertEquals(3, loginAttemptService.getAttempts(adminKey));

        UUID id = UUID.randomUUID();
        Account activeAccount = new Account(id, "admin@gymvip.com", "hash_correcto", Set.of("ADMIN"), "ACTIVO", false);
        when(accountRepository.findByIdentifierIgnoreCase("admin@gymvip.com")).thenReturn(Optional.of(activeAccount));
        when(passwordEncoder.matches("clave_correcta", "hash_correcto")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("admin@gymvip.com", "clave_correcta"), testIp, "Browser");

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(0, loginAttemptService.getAttempts(adminKey));
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    @DisplayName("Bloquear un rol o usuario en AuthService no bloquea a otros usuarios/roles desde la misma IP y aplica límites diferenciados")
    void testUserIsolationAndRoleDifferentiatedLimits() {
        String sameIp = "190.10.20.30";

        Account recepcion = new Account(UUID.randomUUID(), "recepcion@vipcenterfit.pe", "hash_r", Set.of("RECEPCION"), "ACTIVO", false);
        Account auditor = new Account(UUID.randomUUID(), "auditor@vipcenterfit.pe", "hash_a", Set.of("AUDITOR"), "ACTIVO", false);
        Account gerente = new Account(UUID.randomUUID(), "gerente@vipcenterfit.pe", "hash_g", Set.of("GERENTE"), "ACTIVO", false);
        Account cliente = new Account(UUID.randomUUID(), "cliente@vipcenterfit.pe", "hash_c", Set.of("CLIENTE"), "ACTIVO", false);
        Account admin = new Account(UUID.randomUUID(), "admin@vipcenterfit.pe", "hash_ad", Set.of("ADMIN"), "ACTIVO", false);

        when(accountRepository.findByIdentifierIgnoreCase("recepcion@vipcenterfit.pe")).thenReturn(Optional.of(recepcion));
        when(accountRepository.findByIdentifierIgnoreCase("auditor@vipcenterfit.pe")).thenReturn(Optional.of(auditor));
        when(accountRepository.findByIdentifierIgnoreCase("gerente@vipcenterfit.pe")).thenReturn(Optional.of(gerente));
        when(accountRepository.findByIdentifierIgnoreCase("cliente@vipcenterfit.pe")).thenReturn(Optional.of(cliente));
        when(accountRepository.findByIdentifierIgnoreCase("admin@vipcenterfit.pe")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(eq("bad"), anyString())).thenReturn(false);
        when(passwordEncoder.matches(eq("ok_admin"), eq("hash_ad"))).thenReturn(true);

        // RECEPCION: 3 fallos -> bloqueado al 4to intento
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginRequest("recepcion@vipcenterfit.pe", "bad"), sameIp, "UA"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginRequest("recepcion@vipcenterfit.pe", "bad"), sameIp, "UA"));

        // Misma IP: ADMIN sigue pudiendo iniciar sesión normalmente
        AuthResponse adminOk = authService.login(new LoginRequest("admin@vipcenterfit.pe", "ok_admin"), sameIp, "UA");
        assertNotNull(adminOk.token());

        // AUDITOR: 4 fallos -> bloqueado al 5to intento
        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginRequest("auditor@vipcenterfit.pe", "bad"), sameIp, "UA"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginRequest("auditor@vipcenterfit.pe", "bad"), sameIp, "UA"));

        // GERENTE: 5 fallos -> bloqueado al 6to intento
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginRequest("gerente@vipcenterfit.pe", "bad"), sameIp, "UA"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginRequest("gerente@vipcenterfit.pe", "bad"), sameIp, "UA"));

        // CLIENTE: 6 fallos -> bloqueado al 7mo intento
        for (int i = 0; i < 6; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginRequest("cliente@vipcenterfit.pe", "bad"), sameIp, "UA"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginRequest("cliente@vipcenterfit.pe", "bad"), sameIp, "UA"));

        // ADMIN: permite 10 fallos antes de bloquear al 11vo intento
        for (int i = 0; i < 10; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> authService.login(new LoginRequest("admin@vipcenterfit.pe", "bad"), sameIp, "UA"));
        }
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginRequest("admin@vipcenterfit.pe", "bad"), sameIp, "UA"));
    }

    @Test
    @DisplayName("TokenFilter debe purgar reactivamente sesiones expiradas y no autenticar")
    void testExpiredSessionIsPurgedAndNotAuthenticated() throws ServletException, IOException {
        Account account = new Account(UUID.randomUUID(), "test@gymvip.com", "hash", Set.of("ADMIN"), "ACTIVO", false);
        String sessionId = "expired_session_123";
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        String expiredToken = jwtService.generateToken(account, sessionId, past);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        Session expiredSession = new Session(sessionId, account.getId(), past, past.minusHours(1), "agent", "127.0.0.1");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(expiredSession));

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

