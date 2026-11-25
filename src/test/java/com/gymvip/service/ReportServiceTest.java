package com.gymvip.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReportServiceTest {
    private final ReportService service = new ReportService(mock(JdbcTemplate.class));

    @Test void selectedMetricsUseAllowlistAndCanonicalOrder() {
        assertEquals(java.util.List.of("attendance", "lowStock"),
                ReportService.selectedMetrics("lowStock,attendance"));
        assertThrows(IllegalArgumentException.class, () -> ReportService.selectedMetrics("clients;DROP TABLE clients"));
        assertThrows(IllegalArgumentException.class, () -> ReportService.selectedMetrics("attendance,"));
    }

    @Test void bothExportsContainOnlySelectedMetrics() throws Exception {
        var values = new LinkedHashMap<String, BigDecimal>();
        values.put("attendance", BigDecimal.valueOf(12));
        values.put("lowStock", BigDecimal.valueOf(2));
        var snapshot = new ReportService.Snapshot(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 21),
                values, Map.of(), Map.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.xlsx(snapshot)))) {
            var sheet = workbook.getSheet("Metricas");
            assertEquals("Asistencias", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(12, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals("Suplementos con stock bajo", sheet.getRow(2).getCell(0).getStringCellValue());
            assertNull(sheet.getRow(3));
        }
        try (var document = Loader.loadPDF(service.pdf(snapshot))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Asistencias: 12"));
            assertTrue(text.contains("Suplementos con stock bajo: 2"));
            assertFalse(text.contains("Planes asignados"));
        }
    }
}
