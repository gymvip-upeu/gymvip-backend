package com.gymvip.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReminderScheduleStatusControllerTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsSchedulerIndependentlyOfSavedReminderRules(boolean scheduled) throws Exception {
        MockMvcBuilders.standaloneSetup(new ReminderScheduleStatusController(scheduled)).build()
                .perform(get("/api/v1/reminders/schedule").accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(scheduled))
                .andExpect(jsonPath("$.dailyTime").value("10:00"))
                .andExpect(jsonPath("$.timeZone").value("America/Lima"));
    }
}
