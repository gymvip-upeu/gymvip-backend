package com.gymvip.service;

import com.gymvip.dto.request.*;
import com.gymvip.dto.response.AuthResponse;
import com.gymvip.security.JwtService;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PushLifecycleIntegrationTest {
    @Autowired MemberWorkspaceService workspace;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    record Member(UUID client,AuthResponse auth,String session) {}
    private Member member() {
        String doc=String.valueOf(10000000+new java.security.SecureRandom().nextInt(89999999));
        var request=new RegisterClientRequest("Push QA",UUID.randomUUID().toString().substring(0,8),LocalDate.of(1990,1,1),"PE","DNI",doc,"999888777",null);
        var registered=workspace.register(new MemberWorkspaceService.Registration(request,"Solo pruebas push",false),"admin@vipcenterfit.pe");
        var access=workspace.provision(registered.client().id(),"admin@vipcenterfit.pe");
        workspace.changePassword(access.username(),new MemberWorkspaceService.PasswordChange(access.temporaryPassword(),"PushQa123456!"));
        var login=auth.login(new LoginRequest(access.username(),"PushQa123456!"),"127.0.0.1","push-test");
        jdbc.update("UPDATE clients SET consent_app=true WHERE id=?",registered.client().id());
        return new Member(registered.client().id(),login,jwt.extractSessionIdIfValidSignature(login.token()).orElseThrow());
    }
    @BeforeEach void isolateOutbox() { jdbc.update("DELETE FROM notification_outbox"); }
    private MemberWorkspaceService.DeviceTokenRegistration token(String value) {
        return new MemberWorkspaceService.DeviceTokenRegistration(value,"ANDROID","QA");
    }
    private UUID queue(Member member,String device) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status,inbox_item_id,owner_client_id,binding_session_token)
            VALUES (?,?,'PUSH',?,'Privado','No debe salir en payload','PENDING',?,?,?)
            """,id,"qa_"+id,device,"d0000001-0000-0000-0000-000000000001",member.client(),member.session());
        return id;
    }
    @SuppressWarnings("unchecked")
    private void process(FirebaseMessaging messaging) {
        ObjectProvider<FirebaseMessaging> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);
        new TransactionTemplate(transactions).executeWithoutResult(status->new PushOutboxWorker(jdbc,provider,true).process());
    }
    @Test void registrationIsIdempotentAndRejectsAnotherMembersSession() {
        var a=member(); var b=member(); var token=token("qa-registration");
        workspace.registerDeviceToken(a.client(),token,a.session());
        workspace.registerDeviceToken(a.client(),token,a.session());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM member_device_tokens WHERE device_token=? AND is_active",Integer.class,token.token()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->workspace.registerDeviceToken(a.client(),token,b.session()));
    }
    @Test void accountSwitchDoesNotDeliverQueuedMessageOrLetOldOwnerUnregisterNewOwner() {
        var a=member(); var b=member(); String device="qa-switch";
        workspace.registerDeviceToken(a.client(),token(device),a.session());
        UUID job=queue(a,device);
        workspace.registerDeviceToken(b.client(),token(device),b.session());
        workspace.unregisterDeviceToken(a.auth().email(),token(device),a.session());
        assertTrue(jdbc.queryForObject("SELECT is_active FROM member_device_tokens WHERE client_id=? AND device_token=?",Boolean.class,b.client(),device));
        var messaging=mock(FirebaseMessaging.class); process(messaging);
        verifyNoInteractions(messaging);
        assertEquals("SKIPPED",jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?",String.class,job));
    }
    @Test void logoutAndRevokedConsentPreventDeliveryEvenWhenTokenRemains() {
        var a=member(); String device="qa-logout";
        workspace.registerDeviceToken(a.client(),token(device),a.session());
        UUID job=queue(a,device); auth.logout(a.auth().token());
        var messaging=mock(FirebaseMessaging.class); process(messaging); verifyNoInteractions(messaging);
        assertEquals("SKIPPED",jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?",String.class,job));
        var b=member(); workspace.registerDeviceToken(b.client(),token("qa-consent"),b.session());
        UUID consentJob=queue(b,"qa-consent");
        jdbc.update("UPDATE clients SET consent_app=false WHERE id=?",b.client());
        process(messaging); verifyNoInteractions(messaging);
        assertEquals("SKIPPED",jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?",String.class,consentJob));
    }
    @Test void acceptedPushDoesNotMarkInboxReadAndIsNotSentTwice() throws Exception {
        var a=member(); String device="qa-accepted";
        workspace.registerDeviceToken(a.client(),token(device),a.session()); UUID job=queue(a,device);
        var messaging=mock(FirebaseMessaging.class); when(messaging.send(any())).thenReturn("projects/qa/messages/test");
        process(messaging); process(messaging);
        verify(messaging,times(1)).send(any());
        assertEquals("ACCEPTED",jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?",String.class,job));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM member_inbox_reads WHERE client_id=?",Integer.class,a.client()));
    }
    @Test void fanoutUsesNoticeIdAndRepeatingProcessingDoesNotDuplicateDeviceJob() throws Exception {
        var a=member(); workspace.registerDeviceToken(a.client(),token("qa-fanout"),a.session());
        UUID parent=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status,inbox_item_id)
            VALUES (?,?,'PUSH',?,'Aviso','Contenido','PENDING',?)
            """,parent,"qa_"+parent,a.client().toString(),"d0000001-0000-0000-0000-000000000001");
        var messaging=mock(FirebaseMessaging.class); when(messaging.send(any())).thenReturn("projects/qa/messages/fanout");
        process(messaging); process(messaging); process(messaging);
        assertEquals("EXPANDED",jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?",String.class,parent));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE owner_client_id=? AND inbox_item_id=?",Integer.class,a.client(),"d0000001-0000-0000-0000-000000000001"));
        verify(messaging,times(1)).send(any());
    }
    @Test void existingOrderUpdatesKeepTheirOwnAuthenticatedDestination() throws Exception {
        var a=member(); workspace.registerDeviceToken(a.client(),token("qa-order"),a.session());
        UUID parent=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO notification_outbox(id,idempotency_key,channel,recipient,title,payload,status)
            VALUES (?,?,'PUSH',?,'Pedido listo','Contenido privado','PENDING')
            """,parent,"order_notice_"+parent,a.client().toString());
        var messaging=mock(FirebaseMessaging.class); when(messaging.send(any())).thenReturn("projects/qa/messages/order");
        process(messaging); process(messaging);
        var message=org.mockito.ArgumentCaptor.forClass(com.google.firebase.messaging.Message.class);
        verify(messaging).send(message.capture());
        String json=new com.google.gson.Gson().toJson(message.getValue());
        assertTrue(json.contains("GYMVIP_ORDERS")); assertFalse(json.contains("Contenido privado"));
    }
}
