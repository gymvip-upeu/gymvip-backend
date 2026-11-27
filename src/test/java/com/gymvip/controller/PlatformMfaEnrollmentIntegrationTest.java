package com.gymvip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.entity.Account;
import com.gymvip.repository.AccountRepository;
import com.gymvip.security.TotpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformMfaEnrollmentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired TotpService totp;

    @Test
    void enrollmentRevokesRestrictedSessionAndAllowsNewTotpLogin() throws Exception {
        String email = "mfa-fixture-" + UUID.randomUUID() + "@example.test";
        String password = "SoloPrueba-PrimerIngreso2026!";
        accounts.save(new Account(UUID.randomUUID(), email, passwords.encode(password),
                Set.of("SUPER_ADMIN"), "ACTIVO", false));
        byte[] credentials = json.writeValueAsBytes(Map.of("email", email, "password", password));
        var login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnrollmentRequired").value(true)).andReturn();
        String restricted = "Bearer " + json.readTree(login.getResponse().getContentAsByteArray()).get("token").asText();
        mvc.perform(get("/api/v1/preferences").header("Authorization", restricted))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_ENROLLMENT_REQUIRED"));
        var enrollment = mvc.perform(post("/api/v1/security/mfa/enroll").header("Authorization", restricted))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes").isArray()).andReturn();
        String secret = json.readTree(enrollment.getResponse().getContentAsByteArray()).get("secret").asText();
        String code = totp.generateCodeForStep(secret, Instant.now().getEpochSecond() / 30);
        mvc.perform(post("/api/v1/security/mfa/verify").header("Authorization", restricted)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnabled").value(true));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", restricted)).andExpect(status().isUnauthorized());

        var challenge = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true)).andReturn();
        String challengeToken = json.readTree(challenge.getResponse().getContentAsByteArray()).get("challengeToken").asText();
        var verified = mvc.perform(post("/api/v1/auth/mfa/verify-login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("challengeToken", challengeToken, "code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaEnrollmentRequired").value(false)).andReturn();
        String full = "Bearer " + json.readTree(verified.getResponse().getContentAsByteArray()).get("token").asText();
        mvc.perform(get("/api/v1/platform/metrics").header("Authorization", full)).andExpect(status().isOk());
    }
}
