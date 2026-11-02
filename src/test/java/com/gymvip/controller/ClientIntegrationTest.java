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

    @Test
    @DisplayName("DNI peruano inválido es bloqueado con HTTP 400 (HU-01-CA-02)")
    void invalidDniFormatBlocked() throws Exception {
        // DNI con 7 dígitos (debe tener 8)
        RegisterClientRequest shortDni = new RegisterClientRequest(
                "Carlos",
                "Gomez",
                LocalDate.of(1990, 1, 1),
                "PE",
                "DNI",
                "1234567",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortDni)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("8 dígitos")));

        // DNI con caracteres no numéricos
        RegisterClientRequest nonNumericDni = new RegisterClientRequest(
                "Carlos",
                "Gomez",
                LocalDate.of(1990, 1, 1),
                "PE",
                "DNI",
                "1234567A",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonNumericDni)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("8 dígitos")));
    }

    @Test
    @DisplayName("Correo y celular son opcionales; alta sin contacto es exitosa (HU-01-CA-03)")
    void registerClientWithOptionalContactSuccess() throws Exception {
        String docNumber = "8" + (int)(1000000 + Math.random() * 8999999);
        RegisterClientRequest request = new RegisterClientRequest(
                "Lucia",
                "Valdez Vega",
                LocalDate.of(1998, 8, 12),
                "PE",
                "DNI",
                docNumber,
                null,
                null
        );

        String response = mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.memberCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String memberCode = objectMapper.readTree(response).get("memberCode").asText();
        String clientId = objectMapper.readTree(response).get("id").asText();

        // Consulta por código de socio
        mockMvc.perform(get("/api/v1/clients/code/" + memberCode)
                        .header("Authorization", "Bearer " + recepcionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.memberCode").value(memberCode));
    }

    @Test
    @DisplayName("Formato de correo inválido es rechazado con HTTP 400 (HU-01-CA-03)")
    void invalidEmailFormatBlocked() throws Exception {
        String docNumber = "8" + (int)(1000000 + Math.random() * 8999999);
        RegisterClientRequest request = new RegisterClientRequest(
                "Diana",
                "Rojas",
                LocalDate.of(1995, 3, 20),
                "PE",
                "DNI",
                docNumber,
                "+51987654321",
                "correo-invalido-sin-arroba"
        );

        mockMvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + recepcionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("correo electrónico es inválido")));
    }

    @Test
    @DisplayName("Petición sin autenticación es rechazada con HTTP 401 (HU-04-CA-05)")
    void unauthenticatedClientRegistrationBlocked() throws Exception {
        RegisterClientRequest request = new RegisterClientRequest(
                "Anonimo",
                "Anonimo",
                LocalDate.of(1995, 3, 20),
                "PE",
                "DNI",
                "88888888",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
