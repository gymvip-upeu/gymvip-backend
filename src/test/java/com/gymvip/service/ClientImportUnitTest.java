package com.gymvip.service;

import com.gymvip.dto.response.ClientImportPreviewResponse;
import com.gymvip.dto.response.ClientImportResultResponse;
import com.gymvip.entity.Client;
import com.gymvip.exception.ClientValidationException;
import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import com.gymvip.repository.DocumentRepository;
import com.gymvip.service.impl.ClientImportServiceImpl;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientImportUnitTest {

    @Mock private ClientRepository clientRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AuditService auditService;

    private ClientMapper clientMapper;
    private ClientImportService importService;

    @BeforeEach
    void setUp() {
        clientMapper = new ClientMapper();
        importService = new ClientImportServiceImpl(clientRepository, documentRepository, clientMapper, auditService);
    }

    private byte[] createTestExcel(String[][] headers, String[][] rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Miembros");
            if (headers != null) {
                for (int h = 0; h < headers.length; h++) {
                    Row headerRow = sheet.createRow(h);
                    for (int c = 0; c < headers[h].length; c++) {
                        headerRow.createCell(c).setCellValue(headers[h][c]);
                    }
                }
            }
            if (rows != null) {
                int startRow = headers != null ? headers.length : 0;
                for (int r = 0; r < rows.length; r++) {
                    Row dataRow = sheet.createRow(startRow + r);
                    for (int c = 0; c < rows[r].length; c++) {
                        dataRow.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String[][] defaultHeaders() {
        return new String[][]{{
                "Nombres",
                "Apellidos",
                "Fecha de Nacimiento (YYYY-MM-DD)",
                "País (PE)",
                "Tipo Documento (DNI/CE/PASAPORTE)",
                "Número Documento",
                "Teléfono (Opcional)",
                "Correo Electrónico (Opcional)"
        }};
    }

    @Test
    @DisplayName("HU-06 T01: Generar plantilla Excel oficial devuelve .xlsx legible con cabeceras y formato texto")
    void testGenerateTemplate() throws IOException {
        byte[] bytes = importService.generateTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertNotNull(header);
            assertEquals("Nombres", header.getCell(0).getStringCellValue());
            assertEquals("Número Documento", header.getCell(5).getStringCellValue());
            // Verifica que incluya al menos una fila de ejemplo
            assertTrue(sheet.getLastRowNum() >= 1);
        }
    }

    @Test
    @DisplayName("HU-06 CA-02 & CA-03: Vista previa clasifica clientes válidos sin persistir en BD")
    void testPreviewImportValidFile() throws IOException {
        String[][] rows = {
                {"Carlos", "Mendoza", "1992-04-10", "PE", "DNI", "45123456", "+51 912345678", "carlos@example.com"},
                {"Rosa", "Alvarado", "1994-08-22", "PE", "DNI", "78901234", "987654321", "rosa@example.com"}
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "clientes.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(anyString(), anyString(), anyString()))
                .thenReturn(false);

        ClientImportPreviewResponse preview = importService.previewImport(file);

        assertNotNull(preview);
        assertEquals(2, preview.totalRows());
        assertEquals(2, preview.validCount());
        assertEquals(0, preview.duplicateCount());
        assertEquals(0, preview.invalidCount());

        verify(clientRepository, never()).save(any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("HU-06 CA-02: Vista previa detecta menor de edad y DNI con formato inválido")
    void testPreviewImportInvalidRows() throws IOException {
        String underageDate = LocalDate.now().minusYears(16).toString();
        String[][] rows = {
                {"Menor", "De Edad", underageDate, "PE", "DNI", "77112233", "", ""},
                {"Dni", "Invalido", "1990-01-01", "PE", "DNI", "123", "", ""} // 3 dígitos
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "clientes_invalidos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ClientImportPreviewResponse preview = importService.previewImport(file);

        assertEquals(2, preview.totalRows());
        assertEquals(0, preview.validCount());
        assertEquals(2, preview.invalidCount());
        assertTrue(preview.rows().get(0).message().contains("mínimo 18 años"));
        assertTrue(preview.rows().get(1).message().contains("8 dígitos"));
    }

    @Test
    @DisplayName("HU-06 CA-02: Preserva ceros iniciales en DNI/documentos como texto")
    void testPreviewImportLeadingZerosPreserved() throws IOException {
        String[][] rows = {
                {"Manuel", "Prado", "1985-06-14", "PE", "DNI", "08765432", "", ""}
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "ceros.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "08765432"))
                .thenReturn(false);

        ClientImportPreviewResponse preview = importService.previewImport(file);

        assertEquals(1, preview.validCount());
        assertEquals("08765432", preview.rows().get(0).documentNumber());
    }

    @Test
    @DisplayName("HU-06 CA-03: Detecta duplicados internos en archivo y contra base de datos")
    void testPreviewImportDuplicateDetection() throws IOException {
        String[][] rows = {
                {"Primer", "Registro", "1990-01-01", "PE", "DNI", "40506070", "", ""},
                {"Segundo", "RepetidoEnArchivo", "1991-02-02", "PE", "DNI", "40506070", "", ""},
                {"Tercero", "YaEnBaseDatos", "1989-03-03", "PE", "DNI", "99887766", "", ""}
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "duplicados.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "40506070"))
                .thenReturn(false);
        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "99887766"))
                .thenReturn(true);

        ClientImportPreviewResponse preview = importService.previewImport(file);

        assertEquals(3, preview.totalRows());
        assertEquals(1, preview.validCount());
        assertEquals(2, preview.duplicateCount());
        assertEquals("VALID", preview.rows().get(0).status());
        assertEquals("DUPLICATE_IN_FILE", preview.rows().get(1).status());
        assertEquals("DUPLICATE_IN_DATABASE", preview.rows().get(2).status());
    }

    @Test
    @DisplayName("HU-06 CA-04 & CA-05: Confirmar importación crea válidos, omite duplicados y genera auditoría")
    void testConfirmImportSuccess() throws IOException {
        String[][] rows = {
                {"Pedro", "Suárez", "1988-12-05", "PE", "DNI", "11223344", "+51 987111222", "pedro@example.com"},
                {"Luis", "Duplicado", "1990-05-15", "PE", "DNI", "99001122", "", ""}
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "importar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "11223344"))
                .thenReturn(false);
        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "99001122"))
                .thenReturn(true); // Ya existe en BD

        when(clientRepository.existsByMemberCode(anyString())).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientImportResultResponse result = importService.confirmImport(file, "admin@gymvip.local");

        assertNotNull(result);
        assertEquals(2, result.totalRows());
        assertEquals(1, result.createdCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.rejectedCount());

        verify(clientRepository, times(1)).save(any(Client.class));
        verify(auditService, times(1)).recordEvent(
                eq("admin@gymvip.local"),
                eq("IMPORT_CLIENTS"),
                eq("Client"),
                eq("BATCH"),
                contains("1 creados, 1 omitidos")
        );
    }

    @Test
    @DisplayName("HU-06 CA-04: Reimportar el mismo archivo omite todas las filas ya existentes y no duplica")
    void testReimportingSameFileDoesNotDuplicate() throws IOException {
        String[][] rows = {
                {"Pedro", "Suárez", "1988-12-05", "PE", "DNI", "11223344", "", ""}
        };
        byte[] bytes = createTestExcel(defaultHeaders(), rows);
        MockMultipartFile file = new MockMultipartFile("file", "reimportar.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        // Simulamos que el documento ya fue creado en la primera importación
        when(documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber("PE", "DNI", "11223344"))
                .thenReturn(true);

        ClientImportResultResponse result = importService.confirmImport(file, "admin@gymvip.local");

        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        assertEquals("SKIPPED_DUPLICATE", result.results().get(0).status());
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-06 T04: Rechaza archivo vacío o cabeceras corruptas")
    void testRejectsMalformedFile() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "vacio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThrows(ClientValidationException.class, () -> importService.previewImport(emptyFile));

        byte[] wrongHeaders = createTestExcel(new String[][]{{"ColumnaA", "ColumnaB"}}, null);
        MockMultipartFile badHeadersFile = new MockMultipartFile("file", "cabecera_mala.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", wrongHeaders);

        assertThrows(ClientValidationException.class, () -> importService.previewImport(badHeadersFile));
    }

    @Test
    @DisplayName("HU-06 CA-02: Rechaza archivo con cabeceras alteradas o columnas cambiadas de orden")
    void testRejectsAlteredOrSwappedHeaders() throws IOException {
        String[][] swappedHeaders = {{
                "Apellidos",
                "Nombres",
                "Fecha de Nacimiento (YYYY-MM-DD)",
                "País (PE)",
                "Tipo Documento (DNI/CE/PASAPORTE)",
                "Número Documento"
        }};
        byte[] bytes = createTestExcel(swappedHeaders, new String[][]{{"Pérez", "Juan", "1990-01-01", "PE", "DNI", "12345678"}});
        MockMultipartFile file = new MockMultipartFile("file", "swapped.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ClientValidationException ex = assertThrows(ClientValidationException.class, () -> importService.previewImport(file));
        assertTrue(ex.getMessage().contains("alterada o en orden incorrecto"));
    }
}

