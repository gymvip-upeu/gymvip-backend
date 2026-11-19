package com.gymvip.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.reminders.schedule-enabled", havingValue = "true")
class ReminderSchedule {
    private final ReminderService reminders;

    ReminderSchedule(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Lima")
    void runDailyCut() {
        reminders.evaluateReminders("sistema-recordatorios");
    }
}
