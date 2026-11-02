package com.gymvip.service.impl;

import com.gymvip.dto.request.RegisterClientRequest;
import com.gymvip.dto.response.*;
import com.gymvip.entity.Client;
import com.gymvip.exception.ClientValidationException;
import com.gymvip.mapper.ClientMapper;
import com.gymvip.repository.ClientRepository;
import com.gymvip.repository.DocumentRepository;
import com.gymvip.service.AuditService;
import com.gymvip.service.ClientImportService;
import com.gymvip.util.MemberCodeGenerator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ClientImportServiceImpl implements ClientImportService {

    private static final Logger log = LoggerFactory.getLogger(ClientImportServiceImpl.class);
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_DATA_ROWS = 1000;

    private static final String[] EXPECTED_HEADERS = {
            "Nombres",
            "Apellidos",
            "Fecha de Nacimiento (YYYY-MM-DD)",
            "País (PE)",
            "Tipo Documento (DNI/CE/PASAPORTE)",
            "Número Documento",
            "Teléfono (Opcional)",
            "Correo Electrónico (Opcional)"
    };

    private final ClientRepository clientRepository;
    private final DocumentRepository documentRepository;
    private final ClientMapper clientMapper;
    private final AuditService auditService;

    public ClientImportServiceImpl(ClientRepository clientRepository,
                                   DocumentRepository documentRepository,
                                   ClientMapper clientMapper,
                                   AuditService auditService) {
        this.clientRepository = clientRepository;
        this.documentRepository = documentRepository;
        this.clientMapper = clientMapper;
        this.auditService = auditService;
    }

    @Override
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Plantilla_Miembros");

            // Estilos de cabecera
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Estilo para celdas de texto (conserva ceros a la izquierda en DNI / documento)
            DataFormat fmt = workbook.createDataFormat();
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(fmt.getFormat("@"));
            sheet.setDefaultColumnStyle(5, textStyle);

            // Fila de encabezado
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPECTED_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Filas de ejemplo formativas
            String[][] sampleData = {
                    {"Juan Carlos", "Pérez Gómez", "1995-05-15", "PE", "DNI", "08765432", "+51 987654321", "juan.perez@example.com"},
                    {"María Luisa", "Rodríguez Silva", "1990-11-20", "PE", "DNI", "71234567", "912345678", "maria.rodriguez@example.com"},
                    {"Carlos Alberto", "Fernández Ríos", "1988-03-10", "PE", "CARNET_EXTRANJERIA", "001234567", "+51 999888777", ""}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(sampleData[r][c]);
                    if (c == 5) {
                        cell.setCellStyle(textStyle); // Número de documento como texto
                    }
                }
            }

            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                sheet.setColumnWidth(i, 6500);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar plantilla Excel de importación: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar la plantilla de importación de clientes.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ClientImportPreviewResponse previewImport(MultipartFile file) {
        validateFileMetadata(file);

        List<ParsedRow> parsedRows = parseAndValidateRows(file);
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo.xlsx";

        int validCount = 0;
        int duplicateCount = 0;
        int invalidCount = 0;

        List<ClientImportRowPreviewDto> previewRows = new ArrayList<>();
        for (ParsedRow row : parsedRows) {
            if ("VALID".equals(row.status)) {
                validCount++;
            } else if ("DUPLICATE_IN_FILE".equals(row.status) || "DUPLICATE_IN_DATABASE".equals(row.status)) {
                duplicateCount++;
            } else {
                invalidCount++;
            }

            previewRows.add(new ClientImportRowPreviewDto(
                    row.rowNumber,
                    row.firstName,
                    row.lastName,
                    row.birthDate,
                    row.countryCode,
                    row.documentType,
                    row.documentNumber,
                    row.phone,
                    row.email,
                    row.status,
                    row.message
            ));
        }

        return new ClientImportPreviewResponse(
                fileName,
                parsedRows.size(),
                validCount,
                duplicateCount,
                invalidCount,
                previewRows
        );
    }

    @Override
    @Transactional
    public ClientImportResultResponse confirmImport(MultipartFile file, String actorEmail) {
        validateFileMetadata(file);

        List<ParsedRow> parsedRows = parseAndValidateRows(file);
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo.xlsx";

        int createdCount = 0;
        int skippedCount = 0;
        int rejectedCount = 0;

        List<ClientImportRowResultDto> resultRows = new ArrayList<>();

        for (ParsedRow row : parsedRows) {
            String fullName = (row.firstName + " " + row.lastName).trim();

            if ("VALID".equals(row.status)) {
                // Doble chequeo contra base de datos por condición de carrera concurrente
                if (documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(
                        row.countryCode, row.documentType, row.documentNumber)) {
                    skippedCount++;
                    resultRows.add(new ClientImportRowResultDto(
                            row.rowNumber,
                            fullName,
                            row.countryCode,
                            row.documentType,
                            row.documentNumber,
                            null,
                            "SKIPPED_DUPLICATE",
                            "Omitido: el cliente fue registrado recientemente por otra operación."
                    ));
                    continue;
                }

                try {
                    String memberCode;
                    int attempts = 0;
                    do {
                        memberCode = MemberCodeGenerator.generate(row.firstName);
                        attempts++;
                        if (attempts > 15) {
                            memberCode = "VIP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
                            break;
                        }
                    } while (clientRepository.existsByMemberCode(memberCode));

                    RegisterClientRequest request = new RegisterClientRequest(
                            row.firstName,
                            row.lastName,
                            row.birthDate,
                            row.countryCode,
                            row.documentType,
                            row.documentNumber,
                            row.phone,
                            row.email
                    );

                    Client client = clientMapper.toEntity(request, memberCode);
                    Client saved = clientRepository.save(client);
                    createdCount++;

                    resultRows.add(new ClientImportRowResultDto(
                            row.rowNumber,
                            saved.getFullName(),
                            row.countryCode,
                            row.documentType,
                            row.documentNumber,
                            saved.getMemberCode(),
                            "CREATED",
                            "Miembro registrado exitosamente con código " + saved.getMemberCode()
                    ));
                } catch (DataIntegrityViolationException ex) {
                    skippedCount++;
                    resultRows.add(new ClientImportRowResultDto(
                            row.rowNumber,
                            fullName,
                            row.countryCode,
                            row.documentType,
                            row.documentNumber,
                            null,
                            "SKIPPED_DUPLICATE",
                            "Omitido por restricción de integridad única en base de datos."
                    ));
                } catch (Exception ex) {
                    rejectedCount++;
                    resultRows.add(new ClientImportRowResultDto(
                            row.rowNumber,
                            fullName,
                            row.countryCode,
                            row.documentType,
                            row.documentNumber,
                            null,
                            "REJECTED",
                            "No se pudo guardar el registro debido a un error de validación o persistencia en el servidor central."
                    ));
                }
            } else if ("DUPLICATE_IN_FILE".equals(row.status) || "DUPLICATE_IN_DATABASE".equals(row.status)) {
                skippedCount++;
                resultRows.add(new ClientImportRowResultDto(
                        row.rowNumber,
                        fullName,
                        row.countryCode,
                        row.documentType,
                        row.documentNumber,
                        null,
                        "SKIPPED_DUPLICATE",
                        row.message
                ));
            } else {
                rejectedCount++;
                resultRows.add(new ClientImportRowResultDto(
                        row.rowNumber,
                        fullName,
                        row.countryCode,
                        row.documentType,
                        row.documentNumber,
                        null,
                        "REJECTED",
                        row.message
                ));
            }
        }

        // Registro de auditoría
        auditService.recordEvent(
                actorEmail,
                "IMPORT_CLIENTS",
                "Client",
                "BATCH",
                String.format("Importación masiva completada: %d creados, %d omitidos por duplicado, %d rechazados. Archivo: %s",
                        createdCount, skippedCount, rejectedCount, fileName)
        );

        return new ClientImportResultResponse(
                fileName,
                parsedRows.size(),
                createdCount,
                skippedCount,
                rejectedCount,
                Instant.now(),
                actorEmail,
                resultRows
        );
    }

    private void validateFileMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientValidationException("Debe seleccionar un archivo Excel (.xlsx) para importar.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new ClientValidationException("Formato no soportado. Debe cargar un archivo con extensión .xlsx.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ClientValidationException("El archivo supera el límite máximo permitido de 5 MB.");
        }
    }

    private List<ParsedRow> parseAndValidateRows(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ClientValidationException("El archivo Excel no contiene hojas de trabajo.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                throw new ClientValidationException("El archivo Excel no contiene filas de datos para procesar.");
            }

            // Validar cabeceras en fila 0
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new ClientValidationException("La primera fila del archivo debe contener los encabezados oficiales.");
            }

            String[] expectedHeaderKeywords = {
                    "nombre",
                    "apellido",
                    "nacimiento",
                    "pais",
                    "tipo",
                    "documento"
            };

            for (int i = 0; i < 6; i++) {
                Cell cell = headerRow.getCell(i);
                String val = cell != null ? formatter.formatCellValue(cell).trim().toLowerCase() : "";
                if (val.isEmpty()) {
                    throw new ClientValidationException(String.format(
                            "Cabeceras incompletas: falta la columna obligatoria '%s' en la posición %d.",
                            EXPECTED_HEADERS[i], i + 1));
                }
                String normalizedVal = val.replace("í", "i").replace("é", "e").replace("á", "a").replace("ó", "o").replace("ú", "u");
                if (!normalizedVal.contains(expectedHeaderKeywords[i])) {
                    throw new ClientValidationException(String.format(
                            "Columna alterada o en orden incorrecto en posición %d: se esperaba '%s' pero se encontró '%s'. Descargue la plantilla oficial.",
                            i + 1, EXPECTED_HEADERS[i], val));
                }
            }

            int dataRowCount = 0;
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }

                dataRowCount++;
                if (dataRowCount > MAX_DATA_ROWS) {
                    throw new ClientValidationException("El archivo supera el límite de " + MAX_DATA_ROWS + " filas por importación.");
                }

                ParsedRow parsed = parseRow(row, r + 1, formatter, seenInFile);
                rows.add(parsed);
            }

            if (rows.isEmpty()) {
                throw new ClientValidationException("El archivo no contiene registros de clientes válidos para procesar.");
            }

        } catch (ClientValidationException cve) {
            throw cve;
        } catch (Exception e) {
            log.error("Error al procesar archivo Excel: {}", e.getMessage(), e);
            throw new ClientValidationException("El archivo Excel se encuentra dañado o su estructura no corresponde al formato oficial OOXML .xlsx.");
        }

        return rows;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int c = 0; c < 8; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ParsedRow parseRow(Row row, int visualRowNumber, DataFormatter formatter, Set<String> seenInFile) {
        String firstName = getCellString(row.getCell(0), formatter);
        String lastName = getCellString(row.getCell(1), formatter);
        String birthDateStr = getCellString(row.getCell(2), formatter);
        String countryCode = getCellString(row.getCell(3), formatter);
        String documentType = getCellString(row.getCell(4), formatter);
        String documentNumber = getCellString(row.getCell(5), formatter);
        String phone = getCellString(row.getCell(6), formatter);
        String email = getCellString(row.getCell(7), formatter);

        if (countryCode.isBlank()) {
            countryCode = "PE";
        } else {
            countryCode = countryCode.toUpperCase();
        }

        if (documentType.isBlank()) {
            documentType = "DNI";
        } else {
            documentType = documentType.toUpperCase();
        }

        // Validaciones obligatorias
        if (firstName.isBlank()) {
            return new ParsedRow(visualRowNumber, firstName, lastName, null, countryCode, documentType, documentNumber, phone, email, "INVALID", "El nombre es obligatorio.");
        }
        if (lastName.isBlank()) {
            return new ParsedRow(visualRowNumber, firstName, lastName, null, countryCode, documentType, documentNumber, phone, email, "INVALID", "El apellido es obligatorio.");
        }
        if (documentNumber.isBlank()) {
            return new ParsedRow(visualRowNumber, firstName, lastName, null, countryCode, documentType, documentNumber, phone, email, "INVALID", "El número de documento es obligatorio.");
        }

        // Validación de fecha
        LocalDate birthDate;
        Cell birthCell = row.getCell(2);
        if (birthCell != null && birthCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(birthCell)) {
            birthDate = birthCell.getLocalDateTimeCellValue().toLocalDate();
        } else {
            birthDate = parseLocalDate(birthDateStr);
        }

        if (birthDate == null) {
            return new ParsedRow(visualRowNumber, firstName, lastName, null, countryCode, documentType, documentNumber, phone, email, "INVALID", "Fecha de nacimiento inválida (formato esperado: YYYY-MM-DD).");
        }

        // Mayoría de edad (mínimo 18 años)
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (age < 18) {
            return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "INVALID", "El cliente debe ser mayor de edad (mínimo 18 años).");
        }

        // Validación DNI peruano
        if ("PE".equalsIgnoreCase(countryCode) && "DNI".equalsIgnoreCase(documentType)) {
            if (!documentNumber.matches("^[0-9]{8}$")) {
                return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "INVALID", "El DNI peruano debe contener exactamente 8 dígitos numéricos.");
            }
        }

        // Validación teléfono opcional
        if (!phone.isBlank() && !phone.matches("^[+0-9 -]{7,20}$")) {
            return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "INVALID", "Formato de número telefónico inválido.");
        }

        // Validación correo opcional
        if (!email.isBlank() && !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "INVALID", "Formato de correo electrónico inválido.");
        }

        // Detección de duplicado en el mismo archivo
        String compositeKey = countryCode + ":" + documentType + ":" + documentNumber;
        if (seenInFile.contains(compositeKey)) {
            return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "DUPLICATE_IN_FILE", "Documento duplicado dentro del mismo archivo Excel.");
        }
        seenInFile.add(compositeKey);

        // Detección de duplicado contra la base de datos
        if (documentRepository.existsByCountryCodeAndDocumentTypeAndDocumentNumber(countryCode, documentType, documentNumber)) {
            return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "DUPLICATE_IN_DATABASE", "Ya existe un cliente registrado con este documento en el sistema.");
        }

        return new ParsedRow(visualRowNumber, firstName, lastName, birthDate, countryCode, documentType, documentNumber, phone, email, "VALID", "Fila válida lista para importar.");
    }

    private String getCellString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseLocalDate(String str) {
        if (str == null || str.isBlank()) return null;
        String trimmed = str.trim();

        // Probar ISO YYYY-MM-DD
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {}

        // Probar DD/MM/YYYY
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException ignored) {}

        // Probar YYYY/MM/DD
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException ignored) {}

        return null;
    }

    private static class ParsedRow {
        final int rowNumber;
        final String firstName;
        final String lastName;
        final LocalDate birthDate;
        final String countryCode;
        final String documentType;
        final String documentNumber;
        final String phone;
        final String email;
        final String status;
        final String message;

        ParsedRow(int rowNumber, String firstName, String lastName, LocalDate birthDate,
                  String countryCode, String documentType, String documentNumber,
                  String phone, String email, String status, String message) {
            this.rowNumber = rowNumber;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
            this.countryCode = countryCode;
            this.documentType = documentType;
            this.documentNumber = documentNumber;
            this.phone = phone != null && !phone.isBlank() ? phone.trim() : null;
            this.email = email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
            this.status = status;
            this.message = message;
        }
    }
}

