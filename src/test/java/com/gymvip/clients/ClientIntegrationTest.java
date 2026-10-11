package com.gymvip.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.clients.api.ClientController;
import com.gymvip.identity.application.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ClientIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String recepcionToken;

    @BeforeEach
    void setUp() {
        AuthService.LoginResult login = authService.login("recepcion@vipcenterfit.pe", "Recepcion123!", "127.0.0.1");
        recepcionToken = login.getToken();
    }

    @Test
    @DisplayName("Alta de socio adulto exitosa con casillas separadas de consentimiento (HU-01)")
    void registerAdultClientSuccess() throws Exception {
        String docNumber = "7" + (int)(1000000 + Math.random() * 8999999);
        ClientController.RegisterClientRequest request = new ClientController.RegisterClientRequest(
                "Mariana",
                "Torres Ramos",
                LocalDate.of(1996, 5, 20),
                "PE",
                "DNI",
                docNumber,
                "+51988112233",
                "mariana." + docNumber + "@ejemplo.pe",
                true,  // Terms
                true,  // Privacy
                false, // Marketing Email
                true   // Marketing WhatsApp
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Mariana"))
                .andExpect(jsonPath("$.status").value("ACTIVO"));

        // Document duplicate check: same request must return 409 Conflict
        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    @DisplayName("Registro de menor de edad es bloqueado con HTTP 400")
    void registerMinorClientBlocked() throws Exception {
        String docNumber = "6" + (int)(1000000 + Math.random() * 8999999);
        ClientController.RegisterClientRequest request = new ClientController.RegisterClientRequest(
                "Kevin",
                "Menor Perez",
                LocalDate.now().minusYears(15), // 15 years old
                "PE",
                "DNI",
                docNumber,
                "+51988000000",
                "kevin@ejemplo.pe",
                true,
                true,
                false,
                false
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("menores de edad")));
    }
}
