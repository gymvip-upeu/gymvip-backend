package com.gymvip.controller;

import com.gymvip.service.CommsServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChannelStatusControllerTest {
    @Test void inboxDoesNotDependOnUnverifiedWhatsappOrUnconfiguredPush() throws Exception {
        var client=mock(CommsServiceClient.class);
        when(client.getWhatsAppChannelStatus()).thenReturn(Map.of("status","UNVERIFIED"));
        var mvc=MockMvcBuilders.standaloneSetup(new ChannelStatusController(client,true)).build();
        mvc.perform(get("/api/v1/channels/status").accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andExpect(jsonPath("$.channels.whatsapp.status").value("UNVERIFIED"))
            .andExpect(jsonPath("$.channels.app.inbox.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.channels.app.inbox.requiresFirebase").value(false))
            .andExpect(jsonPath("$.channels.app.push.status").value("UNCONFIGURED"))
            .andExpect(jsonPath("$.channels.email").doesNotExist());
    }
    @Test void disabledPushDoesNotDisableInbox() throws Exception {
        var client=mock(CommsServiceClient.class);
        when(client.getWhatsAppChannelStatus()).thenReturn(Map.of("status","DISABLED"));
        MockMvcBuilders.standaloneSetup(new ChannelStatusController(client,false)).build()
            .perform(get("/api/v1/channels/status").accept(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.channels.app.inbox.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.channels.app.push.status").value("DISABLED"));
    }
}
