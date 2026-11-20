package com.gymvip.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CommsChannelStatusTest {
    private void check(String json,String expected) {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new CommsServiceClient("http://comms.test","private-test-key",builder);
        server.expect(requestTo("http://comms.test/api/v1/channels/status"))
            .andExpect(header("X-Service-Key","private-test-key"))
            .andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
        var result=client.getWhatsAppChannelStatus();
        assertEquals(expected,result.get("status"));
        assertEquals(false,result.get("connectionVerified"));
        assertFalse(result.toString().contains("private-test-key"));
        server.verify();
    }
    @Test void readsAuthorityFromCommunications() {
        check("{\"channels\":{\"whatsapp\":{\"enabled\":true,\"status\":\"SIMULATION\",\"configurationComplete\":false}}}","SIMULATION");
        check("{\"channels\":{\"whatsapp\":{\"enabled\":true,\"status\":\"CONFIGURED_REAL\",\"configurationComplete\":true}}}","CONFIGURED_REAL");
    }
    @Test void rejectsInconsistentOrMalformedConfigurationClaims() {
        check("{\"channels\":{\"whatsapp\":{\"enabled\":true,\"status\":\"CONFIGURED_REAL\",\"configurationComplete\":false}}}","UNVERIFIED");
        check("{}","UNVERIFIED");
    }
    @Test void unavailableServiceIsUnknownRatherThanSimulationOrDelivered() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new CommsServiceClient("http://comms.test","private-test-key",builder);
        server.expect(requestTo("http://comms.test/api/v1/channels/status")).andRespond(withServiceUnavailable());
        var result=client.getWhatsAppChannelStatus();
        assertEquals("UNVERIFIED",result.get("status"));
        assertEquals("No se pudo verificar",result.get("message"));
        server.verify();
    }
}
