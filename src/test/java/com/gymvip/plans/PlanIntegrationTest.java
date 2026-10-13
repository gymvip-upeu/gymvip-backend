package com.gymvip.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.identity.application.AuthService;
import com.gymvip.plans.api.PlanController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PlanIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String adminToken;
    private String recepcionToken;

    @BeforeEach
    void setUp() {
        AuthService.LoginResult adminLogin = authService.login("admin@vipcenterfit.pe", "Admin123!", "127.0.0.1");
        adminToken = adminLogin.getToken();

        AuthService.LoginResult recepcionLogin = authService.login("recepcion@vipcenterfit.pe", "Recepcion123!", "127.0.0.1");
        recepcionToken = recepcionLogin.getToken();
    }

    @Test
    @DisplayName("Admin puede crear un plan y consultarlo en el catálogo (HU-06 P0)")
    void adminCanCreateAndListPlan() throws Exception {
        String uniqueName = "Plan Crossfit " + UUID.randomUUID().toString().substring(0, 8);
        PlanController.CreatePlanRequest request = new PlanController.CreatePlanRequest(
                uniqueName,
                "Acceso exclusivo al box de crossfit",
                45,
                new BigDecimal("210.50"),
                "Coaching grupal y acceso a zona abierta"
        );

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(uniqueName))
                .andExpect(jsonPath("$.pricePen").value(210.50))
                .andExpect(jsonPath("$.durationDays").value(45))
                .andExpect(jsonPath("$.status").value("ACTIVO"));

        // List plans and verify it is returned
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem(hasEntry("name", uniqueName))));
    }

    @Test
    @DisplayName("Recepción NO puede crear un plan (HTTP 403 Forbidden - HU-04)")
    void recepcionCannotCreatePlan() throws Exception {
        PlanController.CreatePlanRequest request = new PlanController.CreatePlanRequest(
                "Plan Ilegal Recepcion " + UUID.randomUUID(),
                "Intento no autorizado",
                30,
                new BigDecimal("100.00"),
                "Ninguno"
        );

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Validación: precio o duración negativa rechaza con 400")
    void invalidPlanValuesReturnBadRequest() throws Exception {
        PlanController.CreatePlanRequest request = new PlanController.CreatePlanRequest(
                "Plan Invalido",
                "Precio negativo",
                -5,
                new BigDecimal("-50.00"),
                "Ninguno"
        );

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Regla de negocio: plan con denominación mensual debe tener exactamente 30 días (HU-06)")
    void monthlyPlanMustHaveExactly30Days() throws Exception {
        // Con 31 días debe fallar
        PlanController.CreatePlanRequest invalidMonthly = new PlanController.CreatePlanRequest(
                "Plan Mensual Invalido " + UUID.randomUUID().toString().substring(0, 6),
                "Duración incorrecta de 31 días",
                31,
                new BigDecimal("130.00"),
                "Beneficios"
        );

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMonthly)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("30 días")));

        // Con 30 días exactos debe ser creado
        String validName = "Plan Mensual Exacto " + UUID.randomUUID().toString().substring(0, 6);
        PlanController.CreatePlanRequest validMonthly = new PlanController.CreatePlanRequest(
                validName,
                "Duración correcta de 30 días exactos",
                30,
                new BigDecimal("130.00"),
                "Beneficios"
        );

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMonthly)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(validName))
                .andExpect(jsonPath("$.durationDays").value(30));
    }

    @Test
    @DisplayName("Edición de plan incrementa número de versión y cambio de estado a INACTIVO (HU-06)")
    void updatePlanIncrementsVersionAndInactivates() throws Exception {
        String planName = "Plan Pilates " + UUID.randomUUID().toString().substring(0, 6);
        PlanController.CreatePlanRequest createReq = new PlanController.CreatePlanRequest(
                planName,
                "Version inicial",
                60,
                new BigDecimal("180.00"),
                "Acceso"
        );

        String createResp = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        String planId = objectMapper.readTree(createResp).get("id").asText();

        // Actualizar plan
        PlanController.CreatePlanRequest updateReq = new PlanController.CreatePlanRequest(
                planName + " Actualizado",
                "Segunda version",
                60,
                new BigDecimal("200.00"),
                "Acceso ampliado"
        );

        mockMvc.perform(put("/api/v1/plans/" + planId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.pricePen").value(200.00));

        // Inactivar plan
        mockMvc.perform(patch("/api/v1/plans/" + planId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanController.UpdateStatusRequest("INACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));
    }

    @Test
    @DisplayName("RECEPCION puede consultar el catálogo de planes (HU-06)")
    void recepcionCanListPlans() throws Exception {
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + recepcionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
