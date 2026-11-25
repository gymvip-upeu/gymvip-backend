package com.gymvip.controller;

import com.gymvip.dto.request.*;
import com.gymvip.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @RecordApplicationEvents
class AccountRecoveryIntegrationTest {
    @Autowired MemberWorkspaceService workspace;
    @Autowired MemberAccessService access;
    @Autowired AuthService auth;
    @Autowired PasswordResetService recovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationEvents events;
    @MockitoBean CommsServiceClient comms;
    private static final String OLD="PrimeraClave2026!",NEW="SegundaClave2026!";
    private final AtomicReference<String> link=new AtomicReference<>();
    private String email,session,otherSession;
    private UUID account;
    @BeforeEach void prepare() {
        email="recovery-"+UUID.randomUUID()+"@example.test";
        var client=new RegisterClientRequest("Recuperación","Prueba",LocalDate.of(1990,1,1),"PE","DNI",
            String.valueOf(10000000+new java.security.SecureRandom().nextInt(89999999)),null,email);
        var member=workspace.register(new MemberWorkspaceService.Registration(client,"QA recuperación",true),"admin@vipcenterfit.pe");
        String token=events.stream(MemberAccessService.SecurityEmail.class)
            .filter(e->Objects.equals(e.invitationId(),UUID.fromString(member.credentials().invitationId())))
            .findFirst().orElseThrow().variables().get("link").toString().split("#token=")[1];
        access.activate(token,OLD);
        account=jdbc.queryForObject("SELECT account_id FROM clients WHERE id=?",UUID.class,member.client().id());
        session=auth.login(new LoginRequest(email,OLD),"127.0.0.1","qa-phone").token();
        otherSession=auth.login(new LoginRequest(email,OLD),"127.0.0.1","qa-other").token();
        when(comms.sendPasswordResetEmail(anyString(),anyString(),anyString(),anyString())).thenAnswer(call->{
            link.set(call.getArgument(1));return new CommsServiceClient.CommsDispatchResult(true,"qa-mail","ACEPTADO_PROVEEDOR",null);
        });
    }
    private void requestOwn() throws Exception {
        mvc.perform(post("/api/v1/member-app/password-reset").header("Authorization","Bearer "+session)
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"another@example.test\"}"))
            .andExpect(status().isOk());
    }
    private String token(){return link.get().split("#token=")[1];}
    @Test void ownEmailResetRevokesEverySessionAndOldPasswordAndOnlyWorksOnce() throws Exception {
        requestOwn();verify(comms).sendPasswordResetEmail(eq(email),anyString(),anyString(),anyString());
        assertEquals(email,jdbc.queryForObject("SELECT email FROM accounts WHERE id=?",String.class,account));
        String token=token();assertTrue(jdbc.queryForObject("SELECT expires_at-created_at <= interval '16 minutes' FROM password_reset_tokens WHERE account_id=? AND used_at IS NULL",Boolean.class,account));
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token",token,"newPassword",NEW)))).andExpect(status().isOk());
        for(String revoked:List.of(session,otherSession)) mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+revoked)).andExpect(status().isUnauthorized());
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->auth.login(new LoginRequest(email,OLD),"127.0.0.1","qa"));
        assertNotNull(auth.login(new LoginRequest(email,NEW),"127.0.0.1","qa").token());
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token",token,"newPassword","OtraClave2026!")))).andExpect(status().isUnauthorized());
        assertTrue(events.stream(MemberAccessService.SecurityEmail.class).anyMatch(e->e.to().equals(email) && e.template().equals("password_changed") && !e.variables().containsValue(NEW)));
    }
    @Test void expiredLinkAndReplacedLinkAreRejectedAndResendIsLimited() throws Exception {
        requestOwn();String first=token();
        mvc.perform(post("/api/v1/member-app/password-reset").header("Authorization","Bearer "+session)).andExpect(status().isTooManyRequests());
        recovery.requestReset(new PasswordResetRequest(email),"qa-public");String next=token();
        assertNotEquals(first,next);
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->recovery.confirmReset(new PasswordResetConfirmRequest(first,NEW),"qa"));
        jdbc.update("UPDATE password_reset_tokens SET expires_at=? WHERE account_id=?",LocalDateTime.now().minusMinutes(1),account);
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->recovery.confirmReset(new PasswordResetConfirmRequest(next,NEW),"qa"));
    }
    @Test void failedDeliveryIsExplicitAndCanBeRetriedWithoutChangingPasswordOrSession() throws Exception {
        when(comms.sendPasswordResetEmail(anyString(),anyString(),anyString(),anyString()))
            .thenReturn(new CommsServiceClient.CommsDispatchResult(false,null,"FAILED","fixture"));
        mvc.perform(post("/api/v1/member-app/password-reset").header("Authorization","Bearer "+session)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+session)).andExpect(status().isOk());
        when(comms.sendPasswordResetEmail(anyString(),anyString(),anyString(),anyString()))
            .thenReturn(new CommsServiceClient.CommsDispatchResult(true,"qa-retry","ACEPTADO_PROVEEDOR",null));
        mvc.perform(post("/api/v1/member-app/password-reset").header("Authorization","Bearer "+session)).andExpect(status().isOk());
    }
}
