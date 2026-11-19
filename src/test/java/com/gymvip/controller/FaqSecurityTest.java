package com.gymvip.controller;

import com.gymvip.repository.AccountRepository;
import com.gymvip.repository.SessionRepository;
import com.gymvip.security.JwtService;
import com.gymvip.security.SecurityConfig;
import com.gymvip.security.TokenAuthenticationFilter;
import com.gymvip.service.FaqService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaqController.class)
@Import({SecurityConfig.class, TokenAuthenticationFilter.class})
class FaqSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean FaqService service;
    @MockitoBean SessionRepository sessions;
    @MockitoBean AccountRepository accounts;
    @MockitoBean JwtService jwt;
    static final String ID = "00000000-0000-0000-0000-000000000001";

    @Test void memberCannotReadAdministrativeCatalogOrWrite() throws Exception {
        mvc.perform(get("/api/v1/faqs").with(user("socio").roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/faqs").with(user("socio").roles("CLIENTE"))
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test void receptionCannotPublish() throws Exception {
        mvc.perform(post("/api/v1/faqs/" + ID + "/activate").with(user("recepcion").roles("RECEPCION"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isForbidden());
    }

    @Test void adminPublicationRequiresRevision() throws Exception {
        mvc.perform(post("/api/v1/faqs/" + ID + "/activate").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/faqs/" + ID + "/activate").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isOk());
    }
}
