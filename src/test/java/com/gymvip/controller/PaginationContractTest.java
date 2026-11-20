package com.gymvip.controller;

import com.gymvip.dto.response.*;
import com.gymvip.service.ClientEditingService;
import com.gymvip.service.ClientService;
import com.gymvip.service.CoverageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaginationContractTest {

    @Mock
    private CoverageService coverageService;

    @Mock
    private ClientEditingService clientEditingService;

    @Mock
    private ClientService clientService;

    @InjectMocks
    private CoverageController coverageController;

    @InjectMocks
    private ClientEditingController clientEditingController;

    @InjectMocks
    private ClientController clientController;

    @Test
    @DisplayName("CoverageController.list retorna PageResponse desacoplado de PageImpl preservando campos consumidos por frontend")
    void testCoverageListReturnsStablePageResponse() {
        CoverageResponse item = mock(CoverageResponse.class);
        when(item.memberCode()).thenReturn("VIP-101");
        PageImpl<CoverageResponse> pageImpl = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1L);

        when(coverageService.list(anyString(), anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageImpl);

        PageResponse<CoverageResponse> response = coverageController.list("", "ALERTAS", null, null, null, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("VIP-101", response.content().get(0).memberCode());
        assertEquals(1L, response.totalElements());
        assertEquals(1, response.totalPages());
        assertEquals(20, response.size());
        assertEquals(0, response.number());
        assertEquals(0, response.page());
        assertTrue(response.first());
        assertTrue(response.last());
        assertFalse(response.empty());
        assertEquals(1, response.numberOfElements());
    }

    @Test
    @DisplayName("ClientEditingController.history retorna PageResponse con semántica de auditoría")
    void testClientHistoryReturnsStablePageResponse() {
        UUID clientId = UUID.randomUUID();
        ClientChangeResponse change = mock(ClientChangeResponse.class);
        when(change.field()).thenReturn("TELEFONO");
        PageImpl<ClientChangeResponse> pageImpl = new PageImpl<>(List.of(change), PageRequest.of(0, 20), 1L);

        when(clientEditingService.history(eq(clientId), eq(0), eq(20)))
                .thenReturn(pageImpl);

        PageResponse<ClientChangeResponse> response = clientEditingController.history(clientId, 0, 20);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("TELEFONO", response.content().get(0).field());
        assertEquals(1L, response.totalElements());
        assertTrue(response.last());
        assertFalse(response.empty());
    }

    @Test
    @DisplayName("ClientController.searchClients retorna ResponseEntity<PageResponse> preservando estructura para tabla y selector")
    void testSearchClientsReturnsStablePageResponse() {
        ClientResponse client = mock(ClientResponse.class);
        when(client.memberCode()).thenReturn("VIP-001");
        PageImpl<ClientResponse> pageImpl = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1L);

        when(clientService.searchClients(any(), any()))
                .thenReturn(pageImpl);

        ResponseEntity<PageResponse<ClientResponse>> entity = clientController.searchClients("Carlos", PageRequest.of(0, 20));

        assertNotNull(entity);
        assertTrue(entity.getStatusCode().is2xxSuccessful());
        PageResponse<ClientResponse> body = entity.getBody();
        assertNotNull(body);
        assertEquals(1, body.content().size());
        assertEquals("VIP-001", body.content().get(0).memberCode());
        assertEquals(1L, body.totalElements());
        assertEquals(1, body.totalPages());
    }
}
