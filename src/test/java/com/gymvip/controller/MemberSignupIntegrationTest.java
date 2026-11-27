package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.service.CommsServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"MEMBER_SIGNUP_ENABLED=true", "app.member-access.email-enabled=false"})
@AutoConfigureMockMvc
class MemberSignupIntegrationTest {
    private static final String SIGNUP = "/api/v1/public/member-signups";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CommsServiceClient communications;

    @Test
    void provesBothContactsThenKeepsMemberOutsideOperationalRoutes() throws Exception {
        String email = "signup-" + UUID.randomUUID() + "@example.test";
        String phone = testPhone();
        var emailCode = new AtomicReference<String>();
        var whatsappCode = new AtomicReference<String>();
        var password = new AtomicReference<String>();
        var accepted = new CommsServiceClient.CommsDispatchResult(true, "test-message", "ACEPTADO_PROVEEDOR", null);
        when(communications.sendAccountSecurityEmail(eq(email), anyString(), eq("member_signup_code"), anyMap()))
                .thenAnswer(call -> {
                    emailCode.set((String) call.<Map<String, Object>>getArgument(3).get("code"));
                    return accepted;
                });
        when(communications.sendSignupWhatsAppCode(eq(phone), anyString())).thenAnswer(call -> {
            whatsappCode.set(call.getArgument(1));
            return true;
        });
        when(communications.sendAccountSecurityEmail(eq(email), anyString(), eq("member_signup_password"), anyMap()))
                .thenAnswer(call -> {
                    password.set((String) call.<Map<String, Object>>getArgument(3).get("temporaryPassword"));
                    return accepted;
                });

        var input = signup(email, phone);
        var response = mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.expiresInMinutes").value(10)).andReturn();
        String id = json.readTree(response.getResponse().getContentAsByteArray()).get("challengeId").asText();
        assertNotEquals(emailCode.get(), whatsappCode.get());
        assertNull(password.get());
        mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isTooManyRequests());

        String verification = SIGNUP + "/" + id + "/verify";
        byte[] both = json.writeValueAsBytes(Map.of("emailCode", emailCode.get(), "whatsappCode", whatsappCode.get()));
        mvc.perform(post(verification).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("emailCode", emailCode.get(), "whatsappCode", emailCode.get()))))
                .andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM member_signup_challenges WHERE id=?", Integer.class, UUID.fromString(id)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM accounts WHERE email=?", Integer.class, email));
        mvc.perform(post(verification).contentType(MediaType.APPLICATION_JSON).content(both)).andExpect(status().isAccepted());
        mvc.perform(post(verification).contentType(MediaType.APPLICATION_JSON).content(both)).andExpect(status().isBadRequest());
        assertNotNull(password.get());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM member_identities mi JOIN accounts a ON a.id=mi.account_id WHERE a.email=?
                """, Integer.class, email));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM clients c JOIN accounts a ON a.id=c.account_id WHERE a.email=?
                """, Integer.class, email));

        var temporaryLogin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", password.get()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstAccessPasswordRequired").value(true)).andReturn();
        String temporaryToken = json.readTree(temporaryLogin.getResponse().getContentAsByteArray()).get("token").asText();
        mvc.perform(post("/api/v1/auth/first-password").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + temporaryToken)
                        .content(json.writeValueAsBytes(Map.of("newPassword", "Personal-Pruebas2026!"))))
                .andExpect(status().isNoContent());

        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", "Personal-Pruebas2026!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.directoryOnly").value(true))
                .andExpect(jsonPath("$.firstAccessPasswordRequired").value(false)).andReturn();
        String token = json.readTree(login.getResponse().getContentAsByteArray()).get("token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.directoryOnly").value(true));
        mvc.perform(get(SIGNUP + "/availability").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/api/v1/member-app/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    void rejectsVerificationWhenAChannelCannotDeliver() throws Exception {
        String email = "signup-unavailable-" + UUID.randomUUID() + "@example.test";
        String phone = testPhone();
        when(communications.sendAccountSecurityEmail(eq(email), anyString(), eq("member_signup_code"), anyMap()))
                .thenReturn(new CommsServiceClient.CommsDispatchResult(false, null, "SIMULATED_DISPATCHED", null));
        mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(signup(email, phone)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SIGNUP_UNAVAILABLE"));
        verify(communications, never()).sendSignupWhatsAppCode(eq(phone), anyString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM accounts WHERE email=?", Integer.class, email));
    }

    private byte[] signup(String email, String phone) throws Exception {
        return json.writeValueAsBytes(Map.of("firstName", "Socio", "lastName", "Prueba", "dni", "06123456",
                "birthDate", "1990-01-01", "email", email, "phone", phone, "acceptedTerms", true));
    }

    private static String testPhone() {
        return String.format(Locale.ROOT, "+519%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
