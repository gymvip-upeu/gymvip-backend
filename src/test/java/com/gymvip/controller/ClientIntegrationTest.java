package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        AuthResponse login = authService.login(new LoginRequest("recepcion@vipcenterfit.pe", "Recepcion123!"), "127.0.0.1", "test-agent");
        recepcionToken = login.token();
    }

    @Test
    @DisplayName("Alta de socio adulto exitosa con generación de código MAR-XXXXXX y unicidad (HU-01)")
    void registerAdultClientSuccess() throws Exception {
        String docNumber = "7" + (int)(1000000 + Math.random() * 8999999);
        RegisterClientRequest request = new RegisterClientRequest(
                "Mariana",
                "Torres Ramos",
                LocalDate.of(1996, 5, 20),
                "PE",
                "DNI",
                docNumber,
                "+51988112233",
                "mariana." + docNumber + "@ejemplo.pe"
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.memberCode").value(startsWith("MAR-")))
                .andExpect(jsonPath("$.firstName").value("Mariana"))
                .andExpect(jsonPath("$.status").value("ACTIVO"));

        // 2. Unicidad documental compuesta: mismo documento debe responder 409 Conflict
        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        // 3. Consulta paginada y verificación de datos registrados
        mockMvc.perform(get("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Registro de menor de edad es bloqueado con HTTP 400 (HU-01)")
    void registerMinorClientBlocked() throws Exception {
        String docNumber = "6" + (int)(1000000 + Math.random() * 8999999);
        RegisterClientRequest request = new RegisterClientRequest(
                "Kevin",
                "Menor Perez",
                LocalDate.now().minusYears(15), // 15 años
                "PE",
                "DNI",
                docNumber,
                "+51988000000",
                "kevin@ejemplo.pe"
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("menores de edad")));
    }
}
