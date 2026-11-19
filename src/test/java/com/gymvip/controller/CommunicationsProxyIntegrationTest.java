package com.gymvip.controller;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.service.CommsServiceClient;
import com.gymvip.service.MemberWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "RECEPCION")
class CommunicationsProxyIntegrationTest {
    private static final String PATH = "/api/v1/communications";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberWorkspaceService members;
    @MockitoBean CommsServiceClient communications;

    @Test
    void filtersAndMasksFallbackWhenProviderIsUnavailable() throws Exception {
        String name = "Qa" + UUID.randomUUID().toString().substring(0, 8)
                .chars().mapToObj(value -> String.valueOf((char) ('a' + value % 26)))
                .collect(java.util.stream.Collectors.joining());
        var profile = new RegisterClientRequest(name, "Prueba", LocalDate.of(1990, 1, 1), "PE", "DNI",
                String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000, 99999999)),
                "987654321", null);
        UUID client = members.register(new MemberWorkspaceService.Registration(profile, "", false),
                "admin@vipcenterfit.pe").client().id();
        for (String state : new String[]{"FALLIDO", "ENTREGADO"}) {
            jdbc.update("""
                    INSERT INTO sent_reminder_dispatches (
                        id, idempotency_key, client_id, period_id, channel,
                        rule_type, target_date, status, error_message)
                    VALUES (?, ?, ?, ?, 'WHATSAPP', 'ANTICIPATION_7', CURRENT_DATE, ?, 'detalle interno')
                    """, UUID.randomUUID(), name + state, client, UUID.randomUUID(), state);
        }

        when(communications.getCommunications("FALLIDO", "WHATSAPP", name, 0, 1))
                .thenReturn(Map.of("warning", "Servicio no disponible"));
        mvc.perform(get(PATH).param("status", "FALLIDO").param("channel", "WHATSAPP")
                        .param("search", name).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FALLIDO"))
                .andExpect(jsonPath("$.content[0].recipientMasked").value("***321"))
                .andExpect(jsonPath("$.content[0].errorMessage").value("No se pudo confirmar el envío."));
        mvc.perform(get(PATH).param("status", "inventado")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user("socio").roles("CLIENTE"))).andExpect(status().isForbidden());
    }
}
