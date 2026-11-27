package com.gymvip.saas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.LoginRequest;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
        superAdminToken = authService.login(new LoginRequest("superadmin@gymvip.pe", "SuperAdmin123!"), "127.0.0.1", "test").token();
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

        mockMvc.perform(get("/api/v1/platform/metrics").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueSeparationNotice", containsString("Métricas estrictamente diferenciadas")))
                .andExpect(jsonPath("$.platformMrrPen").isNumber())
                .andExpect(jsonPath("$.gymsRecordedPaymentsVolumePen").isNumber());
    }

    @Test
    void publicGymShowcaseLegalDocsAndAppReleaseWorkWithoutLogin() throws Exception {
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
