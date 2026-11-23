package com.gymvip.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PushOutboxWorkerTest {
    @SuppressWarnings("unchecked")
    private ObjectProvider<FirebaseMessaging> provider(FirebaseMessaging messaging) {
        var provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);
        return provider;
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
    @Test void revokedDeviceOrConsentPreventsQueuedPush() {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=new PushOutboxWorker.Pending(UUID.randomUUID(),"fixture","private-test-device-token","Aviso","Texto",0);
        when(jdbc.query(contains("FROM notification_outbox"),any(RowMapper.class))).thenReturn(List.of(job));
        when(jdbc.queryForObject(contains("c.consent_app=true"),eq(Boolean.class),eq(job.recipient()))).thenReturn(false);
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        verifyNoInteractions(messaging);
        verify(jdbc).update(contains("status='SKIPPED'"),eq(job.id()));
    }
    @Test void providerErrorIsRecordedWithoutTokenOrExceptionDetails() throws Exception {
        var jdbc=mock(JdbcTemplate.class); var messaging=mock(FirebaseMessaging.class);
        var job=new PushOutboxWorker.Pending(UUID.randomUUID(),"fixture","private-test-device-token","Aviso","Texto",2);
        when(jdbc.query(contains("FROM notification_outbox"),any(RowMapper.class))).thenReturn(List.of(job));
        when(jdbc.queryForObject(contains("c.consent_app=true"),eq(Boolean.class),eq(job.recipient()))).thenReturn(true);
        when(messaging.send(any(Message.class))).thenThrow(new IllegalStateException("sensitive details"));
        new PushOutboxWorker(jdbc,provider(messaging),true).process();
        verify(jdbc).update(contains("SET retry_count=?"),eq(3),eq("FAILED"),eq("IllegalStateException"),eq(3),eq(job.id()));
    }
}
