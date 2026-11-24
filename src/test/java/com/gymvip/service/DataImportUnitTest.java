package com.gymvip.service;

import com.gymvip.dto.importing.DataImportDtos.*;
import com.gymvip.service.impl.DataImportServiceImpl;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataImportUnitTest {

    private JdbcTemplate jdbc;
    private AuditService auditService;
    private DataImportTemplateService templateService;
    private DataImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        auditService = mock(AuditService.class);
        templateService = new DataImportTemplateService();
        importService = new DataImportServiceImpl(jdbc, templateService, auditService);
    }

    @Test
    void generatesAllThreeTemplateVariantsWithRequiredSheets() throws Exception {
        for (var variant : DataImportTemplateService.TemplateVariant.values()) {
            byte[] bytes = templateService.generateWorkbook(variant);
            assertNotNull(bytes);
            assertTrue(bytes.length > 5000, "La plantilla debe contener datos y estilos");

            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                assertTrue(wb.getNumberOfSheets() >= 12, "Debe contener al menos 12 hojas estructuradas");
                assertNotNull(wb.getSheet("00_INSTRUCCIONES"), "Debe tener hoja 00_INSTRUCCIONES");
                assertNotNull(wb.getSheet("01_USUARIOS_EMPLEADOS"), "Debe tener hoja 01_USUARIOS_EMPLEADOS");
                assertNotNull(wb.getSheet("02_MIEMBROS"), "Debe tener hoja 02_MIEMBROS");
                assertNotNull(wb.getSheet("03_PLANES"), "Debe tener hoja 03_PLANES");
                assertNotNull(wb.getSheet("04_MEMBRESIAS"), "Debe tener hoja 04_MEMBRESIAS");
                assertNotNull(wb.getSheet("05_PRODUCTOS_SUPLEMENTOS"), "Debe tener hoja 05_PRODUCTOS_SUPLEMENTOS");
                assertNotNull(wb.getSheet("14_CATALOGOS_VALIDOS"), "Debe tener hoja 14_CATALOGOS_VALIDOS");
                assertNotNull(wb.getSheet("15_DICCIONARIO_DE_DATOS"), "Debe tener hoja 15_DICCIONARIO_DE_DATOS");
            }
        }
    }

    @Test
    void previewRejectsEmptyOrNonExcelFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> importService.previewImport(emptyFile));

        MockMultipartFile wrongExt = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> importService.previewImport(wrongExt));
    }

    @Test
    void previewAnalyzesDemoFileCorrectly() {
        byte[] demoBytes = templateService.generateWorkbook(DataImportTemplateService.TemplateVariant.DEMO);
        MockMultipartFile file = new MockMultipartFile("file", "GYMVIP_Datos_Demo.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", demoBytes);

        DataImportPreviewResponse preview = importService.previewImport(file);
        assertNotNull(preview);
        assertEquals("GYMVIP_Datos_Demo.xlsx", preview.filename());
        assertTrue(preview.totalRecords() > 0, "Debe detectar registros en el archivo demo");
        assertFalse(preview.sheetSummaries().isEmpty(), "Debe resumir las hojas detectadas");

        var sheetMiembros = preview.sheetSummaries().stream()
                .filter(s -> s.sheetName().contains("MIEMBROS"))
                .findFirst();
        assertTrue(sheetMiembros.isPresent());
        assertTrue(sheetMiembros.get().totalRows() >= 3, "El demo debe contener al menos 3 socios de prueba");
    }

    @Test
    void confirmImportExecutesTransactionallyAndAudits() {
        byte[] demoBytes = templateService.generateWorkbook(DataImportTemplateService.TemplateVariant.DEMO);
        MockMultipartFile file = new MockMultipartFile("file", "GYMVIP_Datos_Demo.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", demoBytes);

        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Long.class), any())).thenReturn(0L);
        when(jdbc.queryForObject(contains("SELECT id FROM plans"), eq(Long.class), any())).thenReturn(101L);
        when(jdbc.queryForObject(contains("SELECT id FROM clients"), eq(Long.class), any())).thenReturn(201L);
        when(jdbc.queryForObject(contains("SELECT id FROM plan_versions"), eq(Long.class), any())).thenReturn(301L);

        DataImportResultResponse result = importService.confirmImport(file, "admin@vipcenterfit.pe", "CREATE_UPDATE", null);

        assertNotNull(result);
        assertEquals("admin@vipcenterfit.pe", result.actor());
        assertTrue(result.totalInserted() > 0, "Debe insertar registros");
        verify(auditService, atLeastOnce()).recordEvent(eq("admin@vipcenterfit.pe"), eq("DATA_IMPORT_INTEGRAL"), any(), any(), any());
    }

    @Test
    void exportOfficialTemplateFilesToDocumentationFolder() throws Exception {
        java.io.File dir = new java.io.File("../documentos/importaciones/plantillas");
        if (!dir.exists()) dir.mkdirs();

        java.nio.file.Files.write(
                new java.io.File(dir, "GYMVIP_Plantilla_Vacia.xlsx").toPath(),
                templateService.generateWorkbook(DataImportTemplateService.TemplateVariant.EMPTY)
        );
        java.nio.file.Files.write(
                new java.io.File(dir, "GYMVIP_Datos_Demo.xlsx").toPath(),
                templateService.generateWorkbook(DataImportTemplateService.TemplateVariant.DEMO)
        );
        java.nio.file.Files.write(
                new java.io.File(dir, "GYMVIP_Ejemplo_Importacion_Integral.xlsx").toPath(),
                templateService.generateWorkbook(DataImportTemplateService.TemplateVariant.EXAMPLE)
        );

        assertTrue(new java.io.File(dir, "GYMVIP_Plantilla_Vacia.xlsx").exists());
        assertTrue(new java.io.File(dir, "GYMVIP_Datos_Demo.xlsx").exists());
        assertTrue(new java.io.File(dir, "GYMVIP_Ejemplo_Importacion_Integral.xlsx").exists());
    }
}
