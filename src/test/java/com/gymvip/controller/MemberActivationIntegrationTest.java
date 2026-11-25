package com.gymvip.controller;

import com.gymvip.dto.request.*;
import com.gymvip.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @RecordApplicationEvents
class MemberActivationIntegrationTest {
    @Autowired MemberWorkspaceService workspace;
    @Autowired MemberAccessService access;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ApplicationEvents events;
    private static final String ACTOR="admin@vipcenterfit.pe",PERMANENT="MiClavePersonal2026!";
    private MemberWorkspaceService.Registered member(String email,boolean createAccess) {
        String document=String.valueOf(10000000+new java.security.SecureRandom().nextInt(89999999));
        var client=new RegisterClientRequest("Prueba correo","Activación",LocalDate.of(1990,1,1),"PE","DNI",document,"999888777",email);
        return workspace.register(new MemberWorkspaceService.Registration(client,"Solo prueba de acceso",createAccess),ACTOR);
    }
    private MemberWorkspaceService.Registered member() {return member("access-"+UUID.randomUUID()+"@example.test",true);}
    private String token(String invitation) {
        var mail=events.stream(MemberAccessService.SecurityEmail.class)
            .filter(event->event.invitationId()!=null && event.invitationId().toString().equals(invitation)).findFirst().orElseThrow();
        return mail.variables().get("link").toString().split("#token=",2)[1];
    }
    private String login(MemberWorkspaceService.Credentials credentials) {
        return auth.login(new LoginRequest(credentials.username(),credentials.temporaryPassword()),"127.0.0.1","activation-test").token();
    }
    private UUID account(UUID client) {return jdbc.queryForObject("SELECT account_id FROM clients WHERE id=?",UUID.class,client);}

    @Test void realEmailIsLoginAndTemporarySessionCannotReadDataOrRegisterDevices() throws Exception {
        var member=member(); var credentials=member.credentials(); String session=login(credentials);
        assertEquals(jdbc.queryForObject("SELECT email FROM clients WHERE id=?",String.class,member.client().id()),credentials.username());
        UUID account=account(member.client().id());
        assertEquals("PENDIENTE_ACTIVACION",jdbc.queryForObject("SELECT status FROM accounts WHERE id=?",String.class,account));
        assertNotEquals(credentials.temporaryPassword(),jdbc.queryForObject("SELECT password_hash FROM accounts WHERE id=?",String.class,account));
        mvc.perform(get("/api/v1/member-app/session").header("Authorization","Bearer "+session)).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        for(String path:List.of("/api/v1/member-app/me","/api/v1/member-app/inbox","/api/v1/member-app/faqs","/api/v1/catalog/products"))
            mvc.perform(get(path).header("Authorization","Bearer "+session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/member-app/devices").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fixture-device\"}"))
            .andExpect(status().isForbidden());
        assertFalse(jdbc.queryForObject("SELECT email_verified FROM clients WHERE id=?",Boolean.class,member.client().id()));
    }
    @Test void emailActivationIsSingleUseAndInvalidatesTemporaryPasswordAndSession() throws Exception {
        var member=member(); var credentials=member.credentials(); String temporarySession=login(credentials),token=token(credentials.invitationId());
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token,"newPassword",PERMANENT))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activated").value(true));
        assertTrue(jdbc.queryForObject("SELECT email_verified FROM clients WHERE id=?",Boolean.class,member.client().id()));
        assertEquals("ACTIVO",jdbc.queryForObject("SELECT status FROM accounts WHERE id=?",String.class,account(member.client().id())));
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->login(credentials));
        mvc.perform(get("/api/v1/member-app/me").header("Authorization","Bearer "+temporarySession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token,"newPassword",PERMANENT))))
            .andExpect(status().isUnauthorized());
        var normal=auth.login(new LoginRequest(credentials.username(),PERMANENT),"127.0.0.1","activation-test");
        mvc.perform(get("/api/v1/member-app/me").header("Authorization","Bearer "+normal.token())).andExpect(status().isOk());
        assertTrue(events.stream(MemberAccessService.SecurityEmail.class).anyMatch(event->event.template().equals("password_changed") && !event.variables().containsValue(PERMANENT)));
    }
    @Test void firstPasswordInAppActivatesAndRejectsUsingTheTemporaryPasswordAgain() throws Exception {
        var member=member(); var credentials=member.credentials(); String session=login(credentials);
        mvc.perform(post("/api/v1/member-app/first-password").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword",PERMANENT))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requiresLogin").value(true));
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->login(credentials));
        assertFalse(jdbc.queryForObject("SELECT must_change_password FROM accounts WHERE id=?",Boolean.class,account(member.client().id())));
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->access.activate(token(credentials.invitationId()),"OtraClaveSegura2026!"));
        mvc.perform(post("/api/v1/member-app/first-password").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword","OtraClaveSegura2026!")))).andExpect(status().isUnauthorized());
        String normal=auth.login(new LoginRequest(credentials.username(),PERMANENT),"127.0.0.1","activation-test").token();
        mvc.perform(post("/api/v1/member-app/first-password").header("Authorization","Bearer "+normal).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword","OtraClaveSegura2026!")))).andExpect(status().isUnauthorized());
    }
    @Test void firstPasswordRequiresUnexpiredTemporarySessionAndADifferentPassword() throws Exception {
        var member=member();var credentials=member.credentials();String session=login(credentials);
        mvc.perform(post("/api/v1/member-app/first-password").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword",PERMANENT)))).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/member-app/first-password").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword",credentials.temporaryPassword())))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE accounts SET temporary_password_expires_at=? WHERE id=?",LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),account(member.client().id()));
        mvc.perform(post("/api/v1/member-app/first-password").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword",PERMANENT)))).andExpect(status().isUnauthorized());
    }
    @Test void expiryAndResendRotateAllSecretsAndRejectDoubleResend() {
        var member=member(); var credentials=member.credentials(); UUID account=account(member.client().id());
        String old=token(credentials.invitationId());
        jdbc.update("UPDATE accounts SET temporary_password_expires_at=? WHERE id=?",LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),account);
        jdbc.update("UPDATE member_access_invitations SET expires_at=?,created_at=? WHERE account_id=?",LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2),account);
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->login(credentials));
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->access.activate(old,PERMANENT));
        var renewed=access.resend(UUID.fromString(credentials.invitationId()),ACTOR);
        assertNotEquals(credentials.temporaryPassword(),renewed.temporaryPassword());
        assertDoesNotThrow(()->login(renewed));
        assertThrows(com.gymvip.exception.ConflictException.class,()->access.resend(UUID.fromString(renewed.invitationId()),ACTOR));
        assertThrows(com.gymvip.exception.InvalidCredentialsException.class,()->access.activate(old,PERMANENT));
        access.activate(token(renewed.invitationId()),PERMANENT);
        assertThrows(com.gymvip.exception.ConflictException.class,()->access.resend(UUID.fromString(renewed.invitationId()),ACTOR));
    }
    @Test void generatingAccessRequiresEmailAndDoesNotCreateAnotherAccountForAnExistingEmail() {
        assertThrows(IllegalArgumentException.class,()->member(null,true));
        var member=member();
        assertThrows(com.gymvip.exception.ConflictException.class,()->member(member.credentials().username(),true));
        assertDoesNotThrow(()->member(null,false));
    }
    @Test void concurrentActivationOnlyConsumesTheInvitationOnce() throws Exception {
        var member=member();String invitation=token(member.credentials().invitationId());
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> activate=()->{try {access.activate(invitation,PERMANENT);return true;}catch(com.gymvip.exception.InvalidCredentialsException invalid){return false;}};
            var results=pool.invokeAll(List.of(activate,activate));
            assertEquals(1,results.stream().map(result->{try{return result.get()?1:0;}catch(Exception e){throw new RuntimeException(e);}}).mapToInt(Integer::intValue).sum());
        }
        assertTrue(passwords.matches(PERMANENT,jdbc.queryForObject("SELECT password_hash FROM accounts WHERE id=?",String.class,account(member.client().id()))));
    }
}
