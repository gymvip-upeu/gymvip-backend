package com.gymvip.controller;

import com.gymvip.dto.response.ClientImportPreviewResponse;
import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.SecurityConfig;
import com.gymvip.security.TokenAuthenticationFilter;
import com.gymvip.service.ClientImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientImportController.class)
@Import({SecurityConfig.class, TokenAuthenticationFilter.class})
class ClientImportControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClientImportService clientImportService;

    @MockitoBean
    private SessionRepository sessions;

    @MockitoBean
    private AccountRepository accounts;

    @MockitoBean
    private JwtService jwt;

    @Test
    @DisplayName("ADMIN puede solicitar plantilla .xlsx de importación")
    @WithMockUser(username = "admin@gymvip.pe", roles = {"ADMIN"})
    void testAdminCanDownloadTemplate() throws Exception {
        when(clientImportService.generateTemplate()).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/v1/clients/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RECEPCION es rechazado con 403 Forbidden al intentar descargar plantilla")
    @WithMockUser(username = "recepcion@gymvip.pe", roles = {"RECEPCION"})
    void testRecepcionForbiddenDownloadTemplate() throws Exception {
        mvc.perform(get("/api/v1/clients/import/template"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN puede solicitar vista previa de importación con verifyDni")
    @WithMockUser(username = "admin@gymvip.pe", roles = {"ADMIN"})
    void testAdminCanPreviewImport() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "socios.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2});

        when(clientImportService.previewImport(any(), eq(true)))
                .thenReturn(new ClientImportPreviewResponse("socios.xlsx", 1, 1, 0, 0, Collections.emptyList()));

        mvc.perform(multipart("/api/v1/clients/import/preview?verifyDni=true").file(file))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuario no autenticado recibe 401 Unauthorized")
    void testUnauthenticatedForbidden() throws Exception {
        mvc.perform(get("/api/v1/clients/import/template"))
                .andExpect(status().isUnauthorized());
    }
}
