package com.gymvip.service;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.ClientResponse;
import com.gymvip.entity.Client;
import com.gymvip.exception.ClientValidationException;
import com.gymvip.exception.DocumentDuplicateException;
import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import com.gymvip.repository.DocumentRepository;
import com.gymvip.service.impl.ClientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceUnitTest {

    @Mock private ClientRepository clientRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AuditService auditService;

    private ClientMapper clientMapper;
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientMapper = new ClientMapper();
        clientService = new ClientServiceImpl(clientRepository, documentRepository, clientMapper, auditService);
    }

    @Test
    @DisplayName("HU-01 CA-04: Registrar cliente válido mayor de edad persiste en BD, genera código y audita")
    void testRegisterClientSuccess() {
        RegisterClientRequest req = new RegisterClientRequest(
                "Mariana",
                "Torres Luna",
                LocalDate.of(1996, 7, 24),
                "PE",
                "DNI",
                "45892134",
                "+51 987654321",
                "mariana.torres@example.com"
        );

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "45892134"))
                .thenReturn(false);
        when(clientRepository.existsByMemberCode(anyString())).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponse response = clientService.registerClient(req, "admin@gymvip.local");

        assertNotNull(response);
        assertEquals("Mariana", response.firstName());
        assertEquals("Torres Luna", response.lastName());
        assertNotNull(response.memberCode());
        assertTrue(response.memberCode().startsWith("MAR-"));

        verify(auditService, times(1)).recordEvent(
                eq("admin@gymvip.local"),
                eq("REGISTER_CLIENT"),
                eq("Client"),
                anyString(),
                contains("Cliente registrado")
        );
    }

    @Test
    @DisplayName("HU-01 CA-03: Rechaza registro si el cliente es menor de edad (<18 años)")
    void testRegisterClientUnderageRejected() {
        LocalDate underageBirth = LocalDate.now().minusYears(17).minusMonths(6);
        RegisterClientRequest req = new RegisterClientRequest(
                "Lucas",
                "Gómez",
                underageBirth,
                "PE",
                "DNI",
                "78123456",
                null,
                null
        );

        ClientValidationException ex = assertThrows(ClientValidationException.class,
                () -> clientService.registerClient(req, "recepcion@gymvip.local"));

        assertTrue(ex.getMessage().contains("mínimo 18 años"));
        verify(clientRepository, never()).save(any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("HU-01 CA-03: Rechaza DNI peruano que no tenga exactamente 8 dígitos numéricos")
    void testRegisterClientInvalidDni() {
        RegisterClientRequest req7Digits = new RegisterClientRequest(
                "Carlos",
                "Rojas",
                LocalDate.of(1990, 1, 1),
                "PE",
                "DNI",
                "1234567", // 7 dígitos
                null,
                null
        );

        ClientValidationException ex = assertThrows(ClientValidationException.class,
                () -> clientService.registerClient(req7Digits, "admin@gymvip.local"));

        assertTrue(ex.getMessage().contains("exactamente 8 dígitos"));
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-01 CA-05: Rechaza registro cuando el documento ya existe en base de datos")
    void testRegisterClientDuplicateDocument() {
        RegisterClientRequest req = new RegisterClientRequest(
                "Ana",
                "Valdivia",
                LocalDate.of(1992, 5, 10),
                "PE",
                "DNI",
                "44556677",
                null,
                null
        );

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "44556677"))
                .thenReturn(true);

        DocumentDuplicateException ex = assertThrows(DocumentDuplicateException.class,
                () -> clientService.registerClient(req, "admin@gymvip.local"));

        assertTrue(ex.getMessage().contains("Ya existe un cliente"));
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-01 CA-03: Rechaza formato de correo electrónico inválido cuando se proporciona")
    void testRegisterClientInvalidEmail() {
        RegisterClientRequest req = new RegisterClientRequest(
                "Elena",
                "Paz",
                LocalDate.of(1995, 3, 12),
                "PE",
                "DNI",
                "77889900",
                null,
                "correo-invalido-sin-arroba"
        );

        ClientValidationException ex = assertThrows(ClientValidationException.class,
                () -> clientService.registerClient(req, "admin@gymvip.local"));

        assertTrue(ex.getMessage().contains("correo electrónico es inválido"));
    }

    @Test
    @DisplayName("HU-01 CA-05: Ante fallo de persistencia en repositorio, la excepción propaga y no anuncia éxito")
    void testRegisterClientStorageFailureDoesNotAcknowledge() {
        RegisterClientRequest req = new RegisterClientRequest(
                "Jorge",
                "Campos",
                LocalDate.of(1993, 8, 15),
                "PE",
                "DNI",
                "10203040",
                null,
                null
        );

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "10203040"))
                .thenReturn(false);
        when(clientRepository.existsByMemberCode(anyString())).thenReturn(false);
        when(clientRepository.save(any())).thenThrow(new RuntimeException("Simulated database timeout / storage failure"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> clientService.registerClient(req, "admin@gymvip.local"));

        assertTrue(ex.getMessage().contains("storage failure"));
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any());
    }
}

