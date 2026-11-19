package com.gymvip.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ReminderConfigIntegrationTest {
    private static final String CONFIG = "/api/v1/reminders/config";

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsInvalidUpdatesAndUnauthorizedChangesWithoutMutatingConfiguration() throws Exception {
        String valid = """
                {"enabled":false,"anticipationDays":[3,7],"postExpiryDays":5,"channels":["APP"]}
                """;
        mvc.perform(put(CONFIG).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk());
        String saved = readConfig();

        for (String invalid : new String[]{
                valid.replace("[3,7]", "[3,4]"),
                valid.replace("[3,7]", "[3,null]"),
                valid.replace("[3,7]", "null"),
                valid.replace("false", "null"),
                valid.replace(":5", ":6"),
                valid.replace("APP", "OTRO")}) {
            mvc.perform(put(CONFIG).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }

        for (String role : new String[]{"RECEPCION", "CLIENTE"}) {
            mvc.perform(put(CONFIG).with(user("socio").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content(valid.replace("false", "true")))
                    .andExpect(status().isForbidden());
        }
        assertEquals(saved, readConfig());
    }

    private String readConfig() throws Exception {
        return mvc.perform(get(CONFIG)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
