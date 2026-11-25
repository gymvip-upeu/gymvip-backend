package com.gymvip.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of the existing daily scheduler; it does not enable or change it. */
@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderScheduleStatusController {
    private final boolean enabled;

    public ReminderScheduleStatusController(
            @Value("${app.reminders.schedule-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @GetMapping("/schedule")
    public ScheduleStatus getSchedule() {
        return new ScheduleStatus(enabled, "10:00", "America/Lima");
    }

    public record ScheduleStatus(boolean enabled, String dailyTime, String timeZone) {}
}
