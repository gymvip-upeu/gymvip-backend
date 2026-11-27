package com.gymvip.security;

import com.gymvip.controller.AccountSecurityController;
import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.request.UpdateStaffRoleRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.entity.Account;
import com.gymvip.exception.InvalidCredentialsException;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.service.AccountService;
import com.gymvip.service.AuditService;
import com.gymvip.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MfaAndPrivilegedSecurityTest {

    @Test
    void loginWithMfaEnabledRequiresSecondFactorChallengeAndIssuesJwtOnlyAfterValidTotp() {
        AccountRepository accountRepo = mock(AccountRepository.class);
        SessionRepository sessionRepo = mock(SessionRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        LoginAttemptService attempts = new LoginAttemptService();
        JwtService jwtService = new JwtService();
        TotpService totpService = new TotpService();

        AuthServiceImpl authService = new AuthServiceImpl(accountRepo, sessionRepo, encoder, attempts, jwtService);
        ReflectionTestUtils.setField(authService, "totpService", totpService);

        String secret = totpService.generateSecret();
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("superadmin@vipcenterfit.pe");
        account.setPasswordHash(encoder.encode("AdminFuerte2026!"));
        account.setRoles(Set.of("SUPER_ADMIN"));
        account.setStatus("ACTIVO");
        account.setMfaEnabled(true);
        account.setTotpSecret(secret);

        when(accountRepo.findByIdentifierIgnoreCase("superadmin@vipcenterfit.pe")).thenReturn(Optional.of(account));
        when(accountRepo.findById(account.getId())).thenReturn(Optional.of(account));

        AuthResponse step1 = authService.login(new LoginRequest("superadmin@vipcenterfit.pe", "AdminFuerte2026!"), "127.0.0.1", "qa");
        assertTrue(step1.mfaRequired());
        assertNull(step1.token());
        assertNotNull(step1.challengeToken());
        verify(sessionRepo, never()).save(any());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.verifyMfaLogin(step1.challengeToken(), "000000", "127.0.0.1", "qa"));

        long currentStep = Instant.now().getEpochSecond() / 30L;
        String validCode = totpService.generateCodeForStep(secret, currentStep);
        AuthResponse step2 = authService.verifyMfaLogin(step1.challengeToken(), validCode, "127.0.0.1", "qa");
        assertFalse(step2.mfaRequired());
        assertNotNull(step2.token());
        verify(sessionRepo, times(1)).save(any());
    }

    @Test
    void exceptionalPrivilegeRevocationRejectsSelfRevocationAndEnforcesActorMfa() {
        AccountRepository accountRepo = mock(AccountRepository.class);
        SessionRepository sessionRepo = mock(SessionRepository.class);
        TotpService totpService = new TotpService();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AccountService accountService = mock(AccountService.class);
        AuditService auditService = mock(AuditService.class);

        AccountSecurityController controller = new AccountSecurityController(
                accountRepo, sessionRepo, totpService, encoder, accountService, auditService
        );

        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String secret = totpService.generateSecret();
        Account actor = new Account();
        actor.setId(actorId);
        actor.setEmail("root@vipcenterfit.pe");
        actor.setPasswordHash(encoder.encode("ClaveRoot2026!"));
        actor.setRoles(Set.of("SUPER_ADMIN"));
        actor.setMfaEnabled(true);
        actor.setTotpSecret(secret);

        when(accountRepo.findByEmailIgnoreCase("root@vipcenterfit.pe")).thenReturn(Optional.of(actor));
        var auth = new UsernamePasswordAuthenticationToken("root@vipcenterfit.pe", null);
        String validOtp = totpService.generateCodeForStep(secret, Instant.now().getEpochSecond() / 30L);

        // Bloquear auto-revocación
        var selfReq = new AccountSecurityController.PrivilegedRevocationRequest(
                actorId, "RECEPCION", "ClaveRoot2026!", validOtp, "Prueba de auto revocacion"
        );
        assertEquals(400, controller.revokePrivilegedRole(selfReq, auth).getStatusCode().value());

        // Exigir TOTP válido si actor tiene MFA habilitado
        var badOtpReq = new AccountSecurityController.PrivilegedRevocationRequest(
                targetId, "RECEPCION", "ClaveRoot2026!", "999999", "Salida de colaborador"
        );
        assertThrows(InvalidCredentialsException.class, () -> controller.revokePrivilegedRole(badOtpReq, auth));

        // Revocación exitosa con motivo explícito y purga de sesiones
        var validReq = new AccountSecurityController.PrivilegedRevocationRequest(
                targetId, "RECEPCION", "ClaveRoot2026!", validOtp, "Rotación de guardia autorizada"
        );
        var resp = controller.revokePrivilegedRole(validReq, auth);
        assertEquals(200, resp.getStatusCode().value());
        verify(accountService).updateRole(eq(targetId), any(UpdateStaffRoleRequest.class), eq("root@vipcenterfit.pe"));
        verify(sessionRepo).deleteByAccountId(targetId);
        verify(auditService).recordEvent(eq("root@vipcenterfit.pe"), eq("PRIVILEGED_ROLE_REVOKED"), eq("Account"), eq(targetId.toString()), anyString());
    }

    @Test
    void tenantScopeResolverBlocksCrossOrganizationAndCrossBranchAccess() {
        AccountRepository accountRepo = mock(AccountRepository.class);
        var branchRepo = mock(com.gymvip.enterprise.BranchRepository.class);
        TenantScopeResolver resolver = new TenantScopeResolver(accountRepo, branchRepo);

        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        UUID branch1 = UUID.randomUUID();
        UUID branch2 = UUID.randomUUID();
        var first = new com.gymvip.enterprise.BranchEntity();
        first.setOrganizationId(org1);
        var second = new com.gymvip.enterprise.BranchEntity();
        second.setOrganizationId(org2);
        when(branchRepo.findById(branch1)).thenReturn(java.util.Optional.of(first));
        when(branchRepo.findById(branch2)).thenReturn(java.util.Optional.of(second));

        Account branchManager = new Account();
        branchManager.setEmail("sede1@vipcenterfit.pe");
        branchManager.setRoles(Set.of("GERENTE_SEDE"));
        branchManager.setOrganizationId(org1);
        branchManager.setPrimaryBranchId(branch1);

        assertEquals(branch1, resolver.effectiveBranchId(branchManager, null));
        assertThrows(AccessDeniedException.class, () -> resolver.effectiveOrganizationId(branchManager, org2));
        assertThrows(AccessDeniedException.class, () -> resolver.effectiveBranchId(branchManager, branch2));

        Account legacyAdmin = new Account();
        legacyAdmin.setRoles(Set.of("ADMIN"));
        legacyAdmin.setOrganizationId(org1);
        legacyAdmin.setPrimaryBranchId(branch1);
        assertThrows(AccessDeniedException.class, () -> resolver.effectiveOrganizationId(legacyAdmin, org2));
        assertThrows(AccessDeniedException.class, () -> resolver.effectiveBranchId(legacyAdmin, branch2));

        Account superAdmin = new Account();
        superAdmin.setRoles(Set.of("SUPER_ADMIN"));
        superAdmin.setOrganizationId(org1);
        superAdmin.setPrimaryBranchId(branch1);
        assertEquals(org2, resolver.effectiveOrganizationId(superAdmin, org2));
        assertEquals(branch2, resolver.effectiveBranchId(superAdmin, branch2));
    }
}
