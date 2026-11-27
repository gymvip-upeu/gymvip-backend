package com.gymvip.saas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.LoginRequest;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.TotpService;
import com.gymvip.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SaasB2B2CIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String superAdminToken;
    private String adminToken;
    private String gerenteToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        var totp = new TotpService();
        var operator = new Account(UUID.randomUUID(), "platform-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("TestPlatformPassword123!"), Set.of("SUPER_ADMIN"), "ACTIVO", false);
        operator.setMfaEnabled(true);
        operator.setTotpSecret(totp.generateSecret());
        accountRepository.save(operator);
        var challenge = authService.login(new LoginRequest(operator.getEmail(), "TestPlatformPassword123!"),
                "127.0.0.1", "test");
        String code = totp.generateCodeForStep(operator.getTotpSecret(), Instant.now().getEpochSecond() / 30);
        superAdminToken = authService.verifyMfaLogin(challenge.challengeToken(), code, "127.0.0.1", "test").token();
        adminToken = authService.login(new LoginRequest("admin@vipcenterfit.pe", "Admin123!"), "127.0.0.1", "test").token();
        gerenteToken = authService.login(new LoginRequest("gerente@vipcenterfit.pe", "Gerente123!"), "127.0.0.1", "test").token();

        String clientEmail = "socio.saas.test@gymvip.pe";
        if (!accountRepository.existsByEmailIgnoreCase(clientEmail)) {
            accountRepository.save(new Account(
                    UUID.randomUUID(), clientEmail, passwordEncoder.encode("SocioTest123!"),
                    Set.of("CLIENTE"), "ACTIVO", false
            ));
        }
        memberToken = authService.login(new LoginRequest(clientEmail, "SocioTest123!"), "127.0.0.1", "test").token();
    }

    @Test
    void superAdminConsoleStrictlyIsolatedFromGymStaffAndMembers() throws Exception {
        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        var unenrolled = authService.login(new LoginRequest("superadmin@gymvip.pe", "SuperAdmin123!"),
                "127.0.0.1", "test");
        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + unenrolled.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_ENROLLMENT_REQUIRED"));

        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueSeparationNotice", containsString("Métricas estrictamente diferenciadas")))
                .andExpect(jsonPath("$.platformMrrPen").isNumber())
                .andExpect(jsonPath("$.gymsRecordedPaymentsVolumePen").isNumber());
    }

    @Test
    @Transactional
    void publicGymShowcaseLegalDocsAndAppReleaseWorkWithoutLogin() throws Exception {
        UUID org = jdbc.queryForObject("SELECT id FROM organizations WHERE code='vip-center-fit'", UUID.class);
        jdbc.update("UPDATE branches SET is_published=FALSE WHERE organization_id=?", org);
        jdbc.update("UPDATE organizations SET is_published=TRUE WHERE id=?", org);
        jdbc.update("UPDATE organization_commercial_profiles SET publish_status='PUBLICADO' WHERE organization_id=?", org);
        mockMvc.perform(get("/api/v1/public/gyms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", not(hasItem("vip-center-fit"))));

        jdbc.update("UPDATE branches SET is_published=TRUE WHERE organization_id=? AND status='ACTIVO'", org);
        mockMvc.perform(get("/api/v1/public/gyms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].code", hasItem("vip-center-fit")));

        mockMvc.perform(get("/api/v1/legal/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/v1/app/releases/latest?platform=ANDROID&currentVersionCode=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.updateAvailable").doesNotExist());
    }

    private void verifyCompany(UUID requestId, String legalName) throws Exception {
        mockMvc.perform(put("/api/v1/platform/company-requests/{id}/verification", requestId)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "fiscalStatus", "ACTIVO", "fiscalCondition", "HABIDO",
                                "verifiedBusinessName", legalName, "representativeEvidence", "Referencia QA de representación",
                                "contactConfirmed", true))))
                .andExpect(status().isOk());
    }

    @Test
    void approvedCompanyRemainsPrivateAndTemporaryPasswordExpiresAndIsSingleUse() throws Exception {
        String email = "empresa-" + UUID.randomUUID() + "@example.test";
        String ruc = String.valueOf(20_000_000_000L + new java.security.SecureRandom().nextInt(100_000_000));
        var submitted = mockMvc.perform(post("/api/v1/public/company-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "businessName", "Gimnasio QA", "ruc", ruc, "city", "Lima", "district", "Surco",
                                "contactName", "Contacto QA", "contactEmail", email, "contactPhone", "999000000",
                                "legalAccepted", true))))
                .andExpect(status().isCreated()).andReturn();
        UUID requestId = UUID.fromString(objectMapper.readTree(submitted.getResponse().getContentAsByteArray()).get("id").asText());
        mockMvc.perform(post("/api/v1/platform/company-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isConflict());
        verifyCompany(requestId, "Gimnasio QA");
        mockMvc.perform(post("/api/v1/platform/company-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROBADA"))
                .andExpect(jsonPath("$.invitationCode").doesNotExist());
        UUID accountId = jdbc.queryForObject("SELECT id FROM accounts WHERE email = ?", UUID.class, email);
        Integer branches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM branches b JOIN organization_requests r ON b.organization_id = r.approved_organization_id
                WHERE r.id = ?
                """, Integer.class, requestId);
        org.junit.jupiter.api.Assertions.assertEquals(0, branches);

        String temporaryPassword = "ClaveTemporalPruebas727!";
        jdbc.update("""
                UPDATE accounts SET password_hash=?, temporary_password_expires_at=(NOW() AT TIME ZONE 'UTC') - INTERVAL '1 minute'
                WHERE id=?
                """, passwordEncoder.encode(temporaryPassword), accountId);
        byte[] login = objectMapper.writeValueAsBytes(Map.of("email", email, "password", temporaryPassword));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());

        jdbc.update("""
                UPDATE accounts SET temporary_password_expires_at=(NOW() AT TIME ZONE 'UTC') + INTERVAL '1 hour'
                WHERE id=?
                """, accountId);
        var auth = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstAccessPasswordRequired").value(true)).andReturn();
        String accessToken = objectMapper.readTree(auth.getResponse().getContentAsByteArray()).get("token").asText();
        mockMvc.perform(get("/api/v1/organization/commercial-profile").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/first-password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("newPassword", "AccesoEmpresa123!"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVADA",
                jdbc.queryForObject("SELECT status FROM organization_requests WHERE id = ?", String.class, requestId));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", "AccesoEmpresa123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstAccessPasswordRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN_EMPRESA"));
    }

    @Test
    void listingAllGroupPromotionsRequiresStaffWithOrganizationScope() throws Exception {
        mockMvc.perform(get("/api/v1/group-promotions").param("onlyActive", "false"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/group-promotions").param("onlyActive", "false")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/group-promotions").param("onlyActive", "false")
                        .param("organizationId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/group-promotions").param("onlyActive", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void reportsValidateTenantBeforeReadingMetricsOrExporting() throws Exception {
        mockMvc.perform(get("/api/v1/reports")
                        .param("from", "2026-01-01").param("to", "2026-01-02")
                        .header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.memberRegistrations").isNumber())
                .andExpect(jsonPath("$.metrics.announcements").isNumber());

        String otherOrganization = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/reports/export")
                        .param("from", "2026-01-01").param("to", "2026-01-02")
                        .param("format", "xlsx").param("metrics", "memberRegistrations")
                        .param("organizationId", otherOrganization)
                        .header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullB2BOnboardingApprovalCommissionLedgerAndExportPortabilityFlow() throws Exception {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000_000);
        Map<String, Object> reqPayload = Map.ofEntries(
                Map.entry("businessName", "Power Zone Fitness SAC"),
                Map.entry("commercialName", "Power Zone Lima"),
                Map.entry("ruc", ruc),
                Map.entry("city", "Lima"),
                Map.entry("district", "Surco"),
                Map.entry("contactName", "Carlos Vega"),
                Map.entry("contactEmail", "onboarding." + UUID.randomUUID().toString().substring(0, 6) + "@powerzone.pe"),
                Map.entry("contactPhone", "+51999111222"),
                Map.entry("branchesCount", 2),
                Map.entry("estimatedMembers", 250),
                Map.entry("requestedPlanCode", "PRO"),
                Map.entry("legalAccepted", true)
        );

        MvcResult createdReq = mockMvc.perform(post("/api/v1/public/company-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RECIBIDA"))
                .andReturn();

        JsonNode reqNode = objectMapper.readTree(createdReq.getResponse().getContentAsString());
        String requestId = reqNode.get("id").asText();
        String tracking = reqNode.get("trackingCode").asText();

        mockMvc.perform(get("/api/v1/public/company-requests/" + tracking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECIBIDA"));

        verifyCompany(UUID.fromString(requestId), "Power Zone Fitness SAC");
        MvcResult approved = mockMvc.perform(post("/api/v1/platform/company-requests/" + requestId + "/approve?startTrial=true")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROBADA"))
                .andExpect(jsonPath("$.organizationId").isNotEmpty())
                .andReturn();

        String newOrgId = objectMapper.readTree(approved.getResponse().getContentAsString()).get("organizationId").asText();

        // Pending payments never generate commission ledger
        mockMvc.perform(post("/api/v1/platform/commission-ledger/calculate")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationId", newOrgId,
                                "operationType", "MEMBERSHIP",
                                "sourceOperationId", UUID.randomUUID(),
                                "paymentStatus", "PENDING",
                                "grossAmountPen", 150.00
                        ))))
                .andExpect(status().isBadRequest());

        // Settled payment computes transparent commission + net and stays idempotent
        UUID settledOpId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/platform/commission-ledger/calculate")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationId", newOrgId,
                                "operationType", "MEMBERSHIP",
                                "sourceOperationId", settledOpId,
                                "sourceReference", "CULQI-CHG-1001",
                                "paymentStatus", "PAID",
                                "grossAmountPen", 100.00
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platformCommissionPen").value(3.0))
                .andExpect(jsonPath("$.gatewayFeePen").value(3.44))
                .andExpect(jsonPath("$.gymNetAmountPen").value(93.56));

        // Suspend license -> portable historical export MUST STILL remain accessible
        mockMvc.perform(patch("/api/v1/platform/licenses/" + newOrgId + "/status")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "planCode", "PRO",
                                "status", "SUSPENDIDA",
                                "notes", "Mora administrativa de prueba"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDIDA"));

        // Cross-tenant isolation: another gym's gerente CANNOT export newOrgId, and legacy ADMIN CANNOT manage another tenant
        mockMvc.perform(get("/api/v1/platform/export/organization/" + newOrgId)
                        .header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organization/commercial-profile?organizationId=" + newOrgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Own-tenant gerente CAN export their organization, and SUPER_ADMIN CAN export suspended newOrgId for portability
        mockMvc.perform(get("/api/v1/platform/export/organization/11111111-1111-4111-8111-111111111111")
                        .header("Authorization", "Bearer " + gerenteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPortabilityGuarantee", containsString("independientemente del estado comercial")));

        mockMvc.perform(get("/api/v1/platform/export/organization/" + newOrgId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPortabilityGuarantee", containsString("independientemente del estado comercial")));
    }

    @Test
    void customerPhysicalProfileWeeklyPlanNotificationPrefsAndUnverifiedReviewBlockWork() throws Exception {
        // Optional BMI educational endpoint + non-diagnostic notice
        mockMvc.perform(put("/api/v1/member-app/physical-profile")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "heightCm", 174,
                                "weightKg", 72,
                                "primaryGoal", "FUERZA_GENERAL",
                                "experienceLevel", "PRINCIPIANTE",
                                "weeklyAvailabilityDays", 3,
                                "consentSaved", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bmiValue").isNumber())
                .andExpect(jsonPath("$.bmiDisclaimer", containsString("no constituye diagnóstico médico")));

        // Default notification preferences never pre-check promotional channels
        mockMvc.perform(get("/api/v1/member-app/notification-preferences")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channelPushOperational").value(true))
                .andExpect(jsonPath("$.channelPushPromotions").value(false))
                .andExpect(jsonPath("$.channelEmailPromotions").value(false));

        // Account without client membership/attendance CANNOT publish a verified gym review
        mockMvc.perform(post("/api/v1/gyms/11111111-1111-4111-8111-111111111111/reviews")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ratingOverall", 5,
                                "ratingCleanliness", 5,
                                "ratingEquipment", 5,
                                "ratingAttention", 5,
                                "ratingValue", 5,
                                "comment", "Intento de reseña sin vínculo de socio ni asistencias previas."
                        ))))
                .andExpect(status().isForbidden());
    }
}
