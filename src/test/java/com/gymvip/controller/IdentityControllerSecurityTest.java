package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.DniLookupRequest;
import com.gymvip.dto.response.IdentityLookupResponse;
import com.gymvip.enums.IdentityLookupStatus;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.SecurityConfig;
import com.gymvip.security.TokenAuthenticationFilter;
import com.gymvip.service.IdentityLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdentityController.class)
@Import({SecurityConfig.class, TokenAuthenticationFilter.class})
class IdentityControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IdentityLookupService identityLookupService;

    @MockitoBean
    private SessionRepository sessions;

    @MockitoBean
    private AccountRepository accounts;

    @MockitoBean
    private JwtService jwt;

    @Test
    @DisplayName("ADMIN puede consultar DNI y recibir 200 OK")
    @WithMockUser(username = "admin@gymvip.pe", roles = {"ADMIN"})
    void testAdminCanLookupDni() throws Exception {
        when(identityLookupService.lookupDni("72819201"))
                .thenReturn(IdentityLookupResponse.encontrado("72819201", "MARIANA", "TORRES", "RAMOS"));

        DniLookupRequest request = new DniLookupRequest("72819201");

        mvc.perform(post("/api/v1/identity/dni/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCONTRADO"))
                .andExpect(jsonPath("$.dni").value("72819201"))
                .andExpect(jsonPath("$.nombres").value("MARIANA"))
                .andExpect(jsonPath("$.apellidos").value("TORRES RAMOS"));
    }

    @Test
    @DisplayName("RECEPCION puede consultar DNI y recibir 200 OK")
    @WithMockUser(username = "recepcion@gymvip.pe", roles = {"RECEPCION"})
    void testRecepcionCanLookupDni() throws Exception {
        when(identityLookupService.lookupDni("72819201"))
                .thenReturn(IdentityLookupResponse.encontrado("72819201", "MARIANA", "TORRES", "RAMOS"));

        DniLookupRequest request = new DniLookupRequest("72819201");

        mvc.perform(post("/api/v1/identity/dni/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCONTRADO"));
    }

    @Test
    @DisplayName("CLIENTE no puede consultar DNI (403 Forbidden)")
    @WithMockUser(username = "cliente@gymvip.pe", roles = {"CLIENTE"})
    void testClienteForbidden() throws Exception {
        DniLookupRequest request = new DniLookupRequest("72819201");

        mvc.perform(post("/api/v1/identity/dni/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Petición sin autenticación es rechazada (401 Unauthorized)")
    void testUnauthenticatedUnauthorized() throws Exception {
        DniLookupRequest request = new DniLookupRequest("72819201");

        mvc.perform(post("/api/v1/identity/dni/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DNI con menos de 8 dígitos es rechazado con 400 Bad Request")
    @WithMockUser(username = "admin@gymvip.pe", roles = {"ADMIN"})
    void testInvalidDniBadRequest() throws Exception {
        DniLookupRequest request = new DniLookupRequest("1234");

        mvc.perform(post("/api/v1/identity/dni/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
