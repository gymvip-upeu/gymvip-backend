package com.gymvip.controller;

import com.gymvip.service.CommsServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChannelStatusControllerTest {

    @Test
    void inboxDoesNotDependOnUnverifiedWhatsappOrUnconfiguredPush() throws Exception {
        var client = mock(CommsServiceClient.class);
        when(client.getWhatsAppChannelStatus()).thenReturn(Map.of("status", "UNVERIFIED"));
        when(client.getEmailChannelStatus()).thenReturn(Map.of("channel", "EMAIL", "status", "CONFIGURED_REAL", "connectionVerified", false));
        var mvc = MockMvcBuilders.standaloneSetup(new ChannelStatusController(client, true)).build();
        mvc.perform(get("/api/v1/channels/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.whatsapp.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.channels.email.channel").value("EMAIL"))
                .andExpect(jsonPath("$.channels.email.status").value("CONFIGURED_REAL"))
                .andExpect(jsonPath("$.channels.email.connectionVerified").value(false))
                .andExpect(jsonPath("$.channels.push.status").value("UNCONFIGURED"))
                .andExpect(jsonPath("$.channels.app.inbox.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.channels.app.inbox.requiresFirebase").value(false))
                .andExpect(jsonPath("$.channels.app.push.status").value("UNCONFIGURED"));
    }

    @Test
    void disabledPushDoesNotDisableInbox() throws Exception {
        var client = mock(CommsServiceClient.class);
        when(client.getWhatsAppChannelStatus()).thenReturn(Map.of("status", "DISABLED"));
        MockMvcBuilders.standaloneSetup(new ChannelStatusController(client, false)).build()
                .perform(get("/api/v1/channels/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.app.inbox.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.channels.app.push.status").value("DISABLED"))
                .andExpect(jsonPath("$.channels.push.status").value("DISABLED"));
    }
}
