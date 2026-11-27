package com.gymvip.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.util.concurrent.Executor;

@Component
public class MemberAccessEmailDispatcher {
    private final CommsServiceClient comms;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public MemberAccessEmailDispatcher(
            CommsServiceClient comms,
            JdbcTemplate jdbc,
            @Value("${app.member-access.email-enabled:true}") boolean enabled
    ) {
        this.comms = comms;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Async("accountSecurityMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(MemberAccessService.SecurityEmail mail) {
        String status = "TEST_DISABLED";
        String provider = null;
        if (enabled) {
            var result = comms.sendAccountSecurityEmail(mail.to(), mail.subject(), mail.template(), mail.variables());
            status = result.dispatched() ? "ACCEPTED" : result.status().equals("SIMULATED_DISPATCHED") ? "SIMULATED" : "FAILED";
            provider = result.deliveryId();
        }
        if (mail.invitationId() != null) {
            if (com.gymvip.saas.service.CompanyInvitationService.TEMPLATE.equals(mail.template()) || "company_activation".equals(mail.template())) {
                jdbc.update("UPDATE company_activation_invitations SET email_status=?,provider_id=? WHERE id=?", status, provider, mail.invitationId());
                if ("ACCEPTED".equals(status)) {
                    jdbc.update("""
                            UPDATE organization_requests r SET status = 'INVITACION_ENVIADA', updated_at_utc = NOW()
                            FROM company_activation_invitations i
                            WHERE i.request_id = r.id AND i.id = ? AND r.status = 'APROBADA'
                            """, mail.invitationId());
                }
            } else {
                jdbc.update("UPDATE member_access_invitations SET email_status=?,provider_id=? WHERE id=?", status, provider, mail.invitationId());
            }
        }
    }

    @Configuration
    @EnableAsync
    static class MailConfiguration {
        @Bean
        Executor accountSecurityMailExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("account-security-mail-");
            executor.initialize();
            return executor;
        }
    }
}
