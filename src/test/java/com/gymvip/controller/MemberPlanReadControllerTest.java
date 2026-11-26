package com.gymvip.controller;

import com.gymvip.dto.response.PlanResponse;
import com.gymvip.service.PlanService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberPlanReadControllerTest {
    @Test void exposesOnlyActivePlansAndTheirCommercialFields() {
        PlanService service = mock(PlanService.class);
        UUID id = UUID.randomUUID();
        when(service.listPlans("ACTIVO")).thenReturn(List.of(
                new PlanResponse(id, "Mensual", "Acceso", 30, new BigDecimal("120.00"), "Sala", "ACTIVO", 1, null, null, "Consultar recepción"),
                new PlanResponse(UUID.randomUUID(), "Archivado", "", 90, BigDecimal.ONE, "", "INACTIVO", 1, null, null, "")));
        var result = new MemberPlanReadController(service).list();
        assertEquals(1, result.size());
        assertEquals(id, result.getFirst().id());
        assertEquals(new BigDecimal("120.00"), result.getFirst().pricePen());
        assertEquals(30, result.getFirst().durationDays());
        verify(service).listPlans("ACTIVO");
    }
}
