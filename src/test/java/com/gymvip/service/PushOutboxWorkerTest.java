package com.gymvip.service;

import com.google.firebase.messaging.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PushOutboxWorkerTest {
    @SuppressWarnings("unchecked")
    private ObjectProvider<FirebaseMessaging> provider(FirebaseMessaging messaging) {
        var provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);
        return provider;
    }
    private PushOutboxWorker.Pending job(int retries) {
        return new PushOutboxWorker.Pending(UUID.randomUUID(),"fixture","private-test-device-token",UUID.randomUUID().toString(),UUID.randomUUID(),"session-test",retries);
    }
    private void jobs(JdbcTemplate jdbc,PushOutboxWorker.Pending job) {
        when(jdbc.query(contains("FROM notification_outbox"),any(RowMapper.class))).thenReturn(List.of(job));
    }
    private void binding(JdbcTemplate jdbc,PushOutboxWorker.Pending job) {
        when(jdbc.query(contains("FROM member_device_tokens"),any(RowMapper.class),any(Object[].class)))
            .thenReturn(List.of(new PushOutboxWorker.Binding(job.recipient(),job.owner(),UUID.randomUUID(),job.session())));
    }
    @Test void missingFirebaseDoesNotProcessOrPretendToSend() {
        var jdbc=mock(JdbcTemplate.class);
        new PushOutboxWorker(jdbc,provider(null),true).process();
        verifyNoInteractions(jdbc);
    }
    @Test void disabledPushDoesNotProcessEvenWhenSdkExists() {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        new PushOutboxWorker(jdbc,provider(messaging),false).process();
        verifyNoInteractions(jdbc,messaging);
    }
    @Test void revokedSessionDeviceConsentOrNoticePreventsQueuedPush() {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=job(0); jobs(jdbc,job);
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        verifyNoInteractions(messaging);
        verify(jdbc).update(contains("status='SKIPPED',error_message=?"),anyString(),eq(job.id()));
    }
    @Test void providerErrorHasBoundedRetryAndDoesNotExposeTokenOrExceptionDetails() throws Exception {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=job(2); jobs(jdbc,job); binding(jdbc,job);
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("sensitive details"));
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        verify(jdbc).update(contains("SET retry_count=?"),eq(3),eq("FAILED"),eq("IllegalStateException"),eq(true),eq(120),eq(job.id()));
    }
    @Test void acceptedPushContainsOnlyNavigationAndNeverClaimsDelivery() throws Exception {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=job(0); jobs(jdbc,job); binding(jdbc,job);
        when(messaging.send(any(Message.class))).thenReturn("projects/test/messages/accepted");
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        var message=org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(message.capture());
        String json=new com.google.gson.Gson().toJson(message.getValue());
        assertTrue(json.contains(job.itemId())); assertTrue(json.contains("sessionTag"));
        assertFalse(json.contains("notification")); assertFalse(json.contains("plan"));
        verify(jdbc).update(contains("status='ACCEPTED'"),eq("projects/test/messages/accepted"),eq(job.id()));
    }
    @Test void missingDestinationIsNotSentToAnArbitraryScreen() {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=new PushOutboxWorker.Pending(UUID.randomUUID(),"legacy","ALL_MEMBERS",null,null,null,0); jobs(jdbc,job);
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        verifyNoInteractions(messaging);
    }
}
