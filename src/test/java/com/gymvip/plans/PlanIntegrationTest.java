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
}
