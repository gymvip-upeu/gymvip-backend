package com.gymvip.service;

import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import com.gymvip.repository.DocumentRepository;
import com.gymvip.service.impl.ApiPeruDevIdentityProvider;
import com.gymvip.service.impl.ClientImportServiceImpl;
import com.gymvip.service.impl.IdentityLookupServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de regresión para verificar que Spring Boot instancia e inyecta correctamente
 * ClientImportServiceImpl y sus dependencias sin fallar con NoSuchMethodException (<init>).
 */
@SpringBootTest(classes = {
        ClientImportServiceImpl.class,
        IdentityLookupServiceImpl.class,
        ApiPeruDevIdentityProvider.class
})
class ClientImportServiceWiringTest {

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private ClientMapper clientMapper;

    @MockitoBean
    private AuditService auditService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ClientImportService clientImportService;

    @Autowired
    private IdentityLookupService identityLookupService;

    @Autowired
    private IdentityLookupProvider identityLookupProvider;

    @Test
    @DisplayName("Spring ApplicationContext instancia ClientImportServiceImpl mediante su constructor anotado con @Autowired")
    void testClientImportServiceSuccessfullyInstantiated() {
        assertNotNull(clientImportService, "ClientImportService debe ser inyectado por Spring");
        assertTrue(clientImportService instanceof ClientImportServiceImpl);
        assertNotNull(identityLookupService, "IdentityLookupService debe ser inyectado por Spring");
        assertNotNull(identityLookupProvider, "IdentityLookupProvider debe ser inyectado por Spring");
        assertNotNull(applicationContext.getBean("clientImportServiceImpl"));
    }
}
