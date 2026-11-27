package com.gymvip.security;

import com.gymvip.controller.RbacController;
import com.gymvip.dto.request.CreateStaffAccountRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.request.UpdateStaffStatusRequest;
import com.gymvip.enterprise.AccountBranchAssignmentRepository;
import com.gymvip.enterprise.BranchRepository;
import com.gymvip.enterprise.UserPreferenceRepository;
import com.gymvip.entity.Account;
import com.gymvip.entity.Session;
import com.gymvip.exception.LastAdminProtectionException;
import com.gymvip.mapper.AccountMapper;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.AuditEventRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.impl.AccountServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas RBAC Empresarial, TOTP MFA y Protección de Superusuarios")
class EnterpriseRbacAndProtectionTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private AccountBranchAssignmentRepository branchAssignmentRepository;
    @Mock private UserPreferenceRepository preferenceRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        accountService = new AccountServiceImpl(
                accountRepository,
                sessionRepository,
                new AccountMapper(),
                passwordEncoder,
                auditService,
                auditEventRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Impide autoinactivación y autodegradación de administrador")
    void preventsSelfInactivationAndSelfDemotion() {
        UUID adminId = UUID.randomUUID();
        Account admin = new Account(adminId, "super@vipcenterfit.pe", "hash", Set.of("SUPER_ADMIN"), "ACTIVO", true);
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(accountRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));

        assertThrows(LastAdminProtectionException.class, () ->
                accountService.updateStatus(adminId, new UpdateStaffStatusRequest("INACTIVO"), "super@vipcenterfit.pe"));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.updateRole(adminId, new UpdateStaffRoleRequest("RECEPCION"), "super@vipcenterfit.pe"));
    }

    @Test
    @DisplayName("Impide que GERENTE escale o asigne SUPER_ADMIN o ADMIN_EMPRESA")
    void preventsPrivilegeEscalationFromNonAdmin() {
        Account gerenteActor = new Account(UUID.randomUUID(), "gerente@vipcenterfit.pe", "h", Set.of("GERENTE"), "ACTIVO", false);
        when(accountRepository.findByEmailIgnoreCase("gerente@vipcenterfit.pe")).thenReturn(Optional.of(gerenteActor));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.createStaffAccount(
                        new CreateStaffAccountRequest("nuevo@vipcenterfit.pe", "StrongPass123!", "SUPER_ADMIN"),
                        "gerente@vipcenterfit.pe"
                ));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.createStaffAccount(
                        new CreateStaffAccountRequest("nuevo@vipcenterfit.pe", "StrongPass123!", "ADMIN"),
                        "gerente@vipcenterfit.pe"
                ));
    }

    @Test
    @DisplayName("TokenAuthenticationFilter asigna autoridades de rol y permisos granulares")
    void tokenFilterGrantsRoleAndGranularPermissions() throws Exception {
        JwtService jwtService = new JwtService();
        Account coach = new Account(UUID.randomUUID(), "coach@vipcenterfit.pe", "h", Set.of("ENTRENADOR"), "ACTIVO", false);
        String sid = "sess-coach-1";
        LocalDateTime exp = LocalDateTime.now().plusHours(2);
        String token = jwtService.generateToken(coach, sid, exp);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(sessionRepository.findById(sid)).thenReturn(Optional.of(new Session(sid, coach.getId(), exp, LocalDateTime.now(), "App", "127.0.0.1")));
        when(accountRepository.findById(coach.getId())).thenReturn(Optional.of(coach));

        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(sessionRepository, accountRepository, jwtService);
        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ENTRENADOR")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("training.routines.create")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("platform.manage")));
    }

    @Test
    void companyAdministratorCannotCreatePlatformAdministratorOrGrantPlatformRole() {
        var actor = new Account(UUID.randomUUID(), "admin@company.test", "h", Set.of("ADMIN_EMPRESA"), "ACTIVO", false);
        var target = new Account(UUID.randomUUID(), "recepcion@company.test", "h", Set.of("RECEPCION"), "ACTIVO", false);
        when(accountRepository.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.createStaffAccount(new CreateStaffAccountRequest(
                        "nuevo@company.test", "ClaveSegura123!", "SUPER_ADMIN"), actor.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.updateRole(target.getId(), new UpdateStaffRoleRequest("SUPER_ADMIN"), actor.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                accountService.updatePrivilegedRole(target.getId(), new UpdateStaffRoleRequest("SUPER_ADMIN"), actor.getEmail()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("TotpService genera y verifica códigos RFC 6238 y recovery hashes válidos")
    void totpServiceGeneratesAndVerifiesCodes() {
        TotpService totp = new TotpService();
        String secret = totp.generateSecret();
        Instant fixed = Instant.ofEpochSecond(1_700_000_000L);
        long step = fixed.getEpochSecond() / 30L;
        String code = totp.generateCodeForStep(secret, step);

        assertTrue(totp.verifyCode(secret, code, fixed));
        assertFalse(totp.verifyCode(secret, "000000", Instant.ofEpochSecond(900_000_000L)));
        assertTrue(totp.buildProvisioningUri("admin@vipcenterfit.pe", secret).startsWith("otpauth://totp/GYMVIP:"));
        assertEquals(6, totp.generateRecoveryCodes(6).size());
    }

    @Test
    @DisplayName("PermissionCatalog separa permisos de lectura vs administración y expone contexto /api/v1/rbac/me")
    void permissionCatalogAndRbacEndpointExposeEffectivePermissions() {
        assertTrue(PermissionCatalog.hasPermission(Set.of("AUDITOR_EMPRESA"), "reports.read"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("AUDITOR_EMPRESA"), "payments.create"));
        assertTrue(PermissionCatalog.hasPermission(Set.of("ENTRENADOR"), "training.routines.assign"));
        assertFalse(PermissionCatalog.hasPermission(Set.of("CLIENTE"), "training.routines.assign"));

        Account superAdmin = new Account(UUID.randomUUID(), "root@vipcenterfit.pe", "h", Set.of("SUPER_ADMIN"), "ACTIVO", true);
        when(accountRepository.findByEmailIgnoreCase("root@vipcenterfit.pe")).thenReturn(Optional.of(superAdmin));
        RbacController controller = new RbacController(accountRepository, branchAssignmentRepository, preferenceRepository, branchRepository, sessionRepository, auditService);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("root@vipcenterfit.pe");

        var responseEntity = controller.currentAccessContext(auth);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertNotNull(responseEntity.getBody());
        assertTrue(String.valueOf(responseEntity.getBody().get("permissions")).contains("platform.manage"));
    }
}
