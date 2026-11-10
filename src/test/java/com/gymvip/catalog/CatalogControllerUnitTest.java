package com.gymvip.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.request.ReminderConfigRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogControllerUnitTest {

    @Test
    @DisplayName("listSupplements con published=null no lanza NullPointerException (reparación HTTP 500)")
    void listSupplementsWithNullPublishedDoesNotThrowNPE() {
        CatalogService service = mock(CatalogService.class);
        ObjectMapper json = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CatalogController controller = new CatalogController(service, json, validator);

        Authentication auth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();

        when(service.supplements(anyString(), anyString(), anyString(), isNull(), any(), any(), anyBoolean(), anyInt(), anyInt(), eq(false)))
                .thenReturn(new CatalogDtos.Page<>(List.of(), 0, 0, 0));

        // Invocación con published = null (exactamente como lo envía la UI de React en SupplementsPage.jsx)
        assertDoesNotThrow(() -> {
            CatalogDtos.Page<CatalogDtos.Supplement> result = controller.list(
                    "", "", "", null, null, null, false, 0, 12, auth
            );
            assertNotNull(result);
            assertEquals(0, result.totalElements());
        });

        verify(service).supplements("", "", "", null, null, null, false, 0, 12, false);
    }

    @Test
    @DisplayName("listSupplements para cliente fuerza published=true")
    void listSupplementsForCustomerForcesPublishedTrue() {
        CatalogService service = mock(CatalogService.class);
        ObjectMapper json = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CatalogController controller = new CatalogController(service, json, validator);

        Authentication auth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))).when(auth).getAuthorities();

        when(service.supplements(anyString(), anyString(), anyString(), eq(Boolean.TRUE), any(), any(), anyBoolean(), anyInt(), anyInt(), eq(true)))
                .thenReturn(new CatalogDtos.Page<>(List.of(), 0, 0, 0));

        assertDoesNotThrow(() -> {
            CatalogDtos.Page<CatalogDtos.Supplement> result = controller.list(
                    "", "", "", null, null, null, false, 0, 12, auth
            );
            assertNotNull(result);
        });

        verify(service).supplements("", "", "", Boolean.TRUE, null, null, false, 0, 12, true);
    }

    @Test
    @DisplayName("ReminderConfigRequest tolera defaultChannel sin channels (reparación HTTP 400)")
    void reminderConfigRequestSupportsDefaultChannel() {
        ReminderConfigRequest req = new ReminderConfigRequest(
                true,
                List.of(3, 5, 7),
                2,
                null,
                "WHATSAPP"
        );

        assertNotNull(req.channels());
        assertEquals(1, req.channels().size());
        assertEquals("WHATSAPP", req.channels().get(0));

        // Constructor tradicional de 4 parámetros
        ReminderConfigRequest req4 = new ReminderConfigRequest(
                true,
                List.of(3, 5, 7),
                2,
                List.of("EMAIL")
        );
        assertEquals(List.of("EMAIL"), req4.channels());
    }
}

