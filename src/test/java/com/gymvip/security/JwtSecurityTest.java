package com.gymvip.security;

import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtSecurityTest {

    private static final String TEST_SECRET_256_BITS = "TestSecretKeyForTestingJwtSecurityWithMoreThan256Bits2026!";

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtService jwtService;
    private TokenAuthenticationFilter tokenFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "configuredSecret", TEST_SECRET_256_BITS);
        ReflectionTestUtils.setField(jwtService, "expirationHours", 24);
        ReflectionTestUtils.setField(jwtService, "activeProfile", "dev");
        jwtService.init();

        tokenFilter = new TokenAuthenticationFilter(sessionRepository, accountRepository, jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JWT legítimo: debe generar token válido y extraer claims de sujeto, roles y sesión (jti)")
    void testGenerateAndValidateValidToken() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "admin@gymvip.com", "hash", Set.of("ADMIN"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        String token = jwtService.generateToken(account, sessionId, expiresAt);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Optional<Claims> claimsOpt = jwtService.validateAndExtractClaims(token);
        assertTrue(claimsOpt.isPresent());

        Claims claims = claimsOpt.get();
        assertEquals("admin@gymvip.com", claims.getSubject());
        assertEquals(sessionId, claims.getId());
        assertEquals(accountId.toString(), claims.get("accountId"));
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Firma alterada: un token con caracteres manipulados debe ser rechazado inmediatamente")
    void testAlteredSignatureRejected() {
        Account account = new Account(UUID.randomUUID(), "recepcion@gymvip.com", "hash", Set.of("RECEPCION"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String token = jwtService.generateToken(account, sessionId, LocalDateTime.now().plusHours(2));

        // Manipular el último carácter de la firma
        char lastChar = token.charAt(token.length() - 1);
        char alteredChar = (lastChar == 'A') ? 'B' : 'A';
        String tamperedToken = token.substring(0, token.length() - 1) + alteredChar;

        Optional<Claims> claimsOpt = jwtService.validateAndExtractClaims(tamperedToken);
        assertTrue(claimsOpt.isEmpty(), "Un token con firma alterada no debe producir claims válidos");
    }

    @Test
    @DisplayName("Clave distinta: un token firmado con una clave secreta no autorizada debe ser rechazado")
    void testDifferentKeySignatureRejected() {
        SecretKey rogueKey = Keys.hmacShaKeyFor("RogueAttackerSecretKeyMustBeAtLeast256BitsForHmacSha256TestingOnly!".getBytes(StandardCharsets.UTF_8));
        String rogueToken = Jwts.builder()
                .subject("attacker@evil.com")
                .id(UUID.randomUUID().toString())
                .claim("accountId", UUID.randomUUID().toString())
                .claim("roles", Set.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(rogueKey, Jwts.SIG.HS256)
                .compact();

        Optional<Claims> claimsOpt = jwtService.validateAndExtractClaims(rogueToken);
        assertTrue(claimsOpt.isEmpty(), "Un token firmado con clave ajena debe ser rechazado sin excepción");
    }

    @Test
    @DisplayName("Token expirado: debe ser rechazado por expiración temporal")
    void testExpiredTokenRejected() {
        Account account = new Account(UUID.randomUUID(), "socio@gymvip.com", "hash", Set.of("CLIENTE"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime past = LocalDateTime.now().minusHours(1);

        String expiredToken = jwtService.generateToken(account, sessionId, past);

        Optional<Claims> claimsOpt = jwtService.validateAndExtractClaims(expiredToken);
        assertTrue(claimsOpt.isEmpty(), "Un token expirado no debe ser válido");
    }

    @Test
    @DisplayName("Perfil prod: debe lanzar IllegalStateException si JWT_SECRET tiene menos de 32 caracteres (256 bits)")
    void testProdProfileRejectsShortSecret() {
        JwtService prodJwtService = new JwtService();
        ReflectionTestUtils.setField(prodJwtService, "configuredSecret", "corta_insegura");
        ReflectionTestUtils.setField(prodJwtService, "activeProfile", "prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, prodJwtService::init);
        assertTrue(ex.getMessage().contains("SEGURIDAD CRÍTICA"));
        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    @DisplayName("Filtro con JWT válido y sesión activa: debe autenticar con los roles correctos en SecurityContextHolder")
    void testValidTokenAuthenticatesInSecurityContext() throws ServletException, IOException {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "admin@gymvip.com", "hash", Set.of("ADMIN"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);

        String jwtToken = jwtService.generateToken(account, sessionId, expiresAt);
        Session session = new Session(sessionId, accountId, expiresAt, LocalDateTime.now(), "Mozilla", "127.0.0.1");

        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtToken);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        tokenFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin@gymvip.com", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Revocación en tiempo real (post-logout): sesión eliminada de BD rechaza inmediatamente el JWT")
    void testRevokedSessionPostLogoutDoesNotAuthenticate() throws ServletException, IOException {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "recepcion@gymvip.com", "hash", Set.of("RECEPCION"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);

        String jwtToken = jwtService.generateToken(account, sessionId, expiresAt);

        // Simulamos que el usuario cerró sesión -> sessionRepository ya NO encuentra la sesión por sessionId
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtToken);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        tokenFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Una sesión revocada en BD no debe autenticarse");
        verify(accountRepository, never()).findById(any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Cuenta inactiva: token legítimo con sesión en BD no autentica si el estado de la cuenta es INACTIVO")
    void testInactiveAccountTokenDoesNotAuthenticate() throws ServletException, IOException {
        UUID accountId = UUID.randomUUID();
        Account inactiveAccount = new Account(accountId, "inactivo@gymvip.com", "hash", Set.of("RECEPCION"), "INACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(8);

        String jwtToken = jwtService.generateToken(inactiveAccount, sessionId, expiresAt);
        Session session = new Session(sessionId, accountId, expiresAt, LocalDateTime.now(), "Mozilla", "127.0.0.1");

        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtToken);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(inactiveAccount));

        tokenFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Cuentas inactivas no deben ser autenticadas");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Token expirado reactivo: purga la sesión de BD y no establece autenticación")
    void testExpiredTokenPurgesSessionAndDoesNotAuthenticate() throws ServletException, IOException {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "admin@gymvip.com", "hash", Set.of("ADMIN"), "ACTIVO", false);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime past = LocalDateTime.now().minusHours(2);

        String expiredJwt = jwtService.generateToken(account, sessionId, past);
        Session expiredSession = new Session(sessionId, accountId, past, past.minusHours(1), "agent", "127.0.0.1");

        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredJwt);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(expiredSession));

        tokenFilter.doFilter(request, response, filterChain);

        verify(sessionRepository, times(1)).delete(expiredSession);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
