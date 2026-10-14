package com.gymvip.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.identity.api.AccountController;
import com.gymvip.identity.application.port.in.AuthUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AccountManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUseCase authUseCase;

    private String adminToken;
    private String recepcionToken;

    @BeforeEach
    void setUp() {
        AuthUseCase.LoginResult adminLogin = authUseCase.login("admin@vipcenterfit.pe", "Admin123!", "127.0.0.1");
        adminToken = adminLogin.token();

        AuthUseCase.LoginResult recepcionLogin = authUseCase.login("recepcion@vipcenterfit.pe", "Recepcion123!", "127.0.0.1");
        recepcionToken = recepcionLogin.token();
    }

    @Test
    @DisplayName("ADMIN puede listar nómina de cuentas de personal (HU-04)")
    void adminCanListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].email").isNotEmpty())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist()); // No expone contraseñas
    }

    @Test
    @DisplayName("RECEPCION NO tiene permiso para listar cuentas de personal (HTTP 403 - HU-04)")
    void recepcionForbiddenFromAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + recepcionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Petición sin autenticación es rechazada (HTTP 401)")
    void unauthenticatedForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN registra nueva cuenta de trabajador (RECEPCION) y valida duplicados")
    void adminCreatesWorkerAccount() throws Exception {
        String uniqueEmail = "cajero." + UUID.randomUUID().toString().substring(0, 6) + "@vipcenterfit.pe";
        AccountController.CreateStaffAccountRequest request = new AccountController.CreateStaffAccountRequest(
                uniqueEmail,
                "Temporal1234!",
                "RECEPCION"
        );

        // Crear cuenta exitosamente
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(uniqueEmail.toLowerCase()))
                .andExpect(jsonPath("$.status").value("ACTIVO"))
                .andExpect(jsonPath("$.roles", hasItem("RECEPCION")));

        // Intento duplicado con mismo correo devuelve 409 Conflict
        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    @DisplayName("Rechazo de creación con rol no autorizado (ej. CLIENTE o ENTRENADOR) con HTTP 400")
    void invalidStaffRoleRejected() throws Exception {
        AccountController.CreateStaffAccountRequest request = new AccountController.CreateStaffAccountRequest(
                "usuario.invalido@vipcenterfit.pe",
                "Password123!",
                "ENTRENADOR"
        );

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ADMIN puede cambiar el rol y el estado de un trabajador")
    void adminCanUpdateRoleAndStatus() throws Exception {
        String uniqueEmail = "staff." + UUID.randomUUID().toString().substring(0, 6) + "@vipcenterfit.pe";
        AccountController.CreateStaffAccountRequest request = new AccountController.CreateStaffAccountRequest(
                uniqueEmail,
                "Password123!",
                "RECEPCION"
        );

        String createResp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accountId = objectMapper.readTree(createResp).get("id").asText();

        // Ascender a ADMIN
        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountController.UpdateRoleRequest("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")));

        // Inactivar cuenta
        mockMvc.perform(patch("/api/v1/accounts/" + accountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountController.UpdateStatusRequest("INACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));
    }

    @Test
    @DisplayName("Protección crítica: se impide inactivar al último administrador activo del sistema")
    void cannotInactivateLastAdmin() throws Exception {
        String listResp = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var nodes = objectMapper.readTree(listResp);
        String lastAdminId = null;
        int adminCount = 0;
        for (var node : nodes) {
            boolean isAdmin = false;
            for (var role : node.get("roles")) {
                if (role.asText().equals("ADMIN") || role.asText().equals("SUPERADMIN")) {
                    isAdmin = true;
                    break;
                }
            }
            if (isAdmin && "ACTIVO".equalsIgnoreCase(node.get("status").asText())) {
                adminCount++;
                lastAdminId = node.get("id").asText();
            }
        }

        if (adminCount == 1 && lastAdminId != null) {
            mockMvc.perform(patch("/api/v1/accounts/" + lastAdminId + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AccountController.UpdateStatusRequest("INACTIVO"))))
                    .andExpect(status().isConflict());
        }
    }
}
