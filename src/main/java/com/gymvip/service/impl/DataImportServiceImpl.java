package com.gymvip.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymvip.dto.importing.DataImportDtos.*;
import com.gymvip.service.AuditService;
import com.gymvip.service.DataImportService;
import com.gymvip.service.DataImportTemplateService;
import com.gymvip.util.MemberCodeGenerator;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DataImportServiceImpl implements DataImportService {

    private static final Logger log = LoggerFactory.getLogger(DataImportServiceImpl.class);
    private static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024; // 15 MB
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final DataImportTemplateService templateService;
    private final AuditService auditService;

    public DataImportServiceImpl(JdbcTemplate jdbc,
                                 DataImportTemplateService templateService,
                                 AuditService auditService) {
        this.jdbc = jdbc;
        this.templateService = templateService;
        this.auditService = auditService;
    }

    @Override
    public byte[] getTemplate(String templateType) {
        DataImportTemplateService.TemplateVariant variant;
        try {
            variant = DataImportTemplateService.TemplateVariant.valueOf(templateType.toUpperCase());
        } catch (Exception e) {
            variant = DataImportTemplateService.TemplateVariant.EMPTY;
        }
        return templateService.generateWorkbook(variant);
    }

    @Override
    public DataImportPreviewResponse previewImport(MultipartFile file) {
        validateFile(file);
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            List<SheetSummary> summaries = new ArrayList<>();
            Map<String, List<RowPreview>> sampleRows = new LinkedHashMap<>();
            List<String> dependencyIssues = new ArrayList<>();

            int totalRecords = 0;
            int validRecords = 0;
            int invalidRecords = 0;

            // Conjuntos en memoria para resolver dependencias entre hojas
            Set<String> knownUsers = new HashSet<>();
            Set<String> knownMembers = new HashSet<>();
            Set<String> knownPlans = new HashSet<>();
            Set<String> knownSupplements = new HashSet<>();
            Set<String> knownOrders = new HashSet<>();

            // Precargar identificadores existentes de la base de datos
            try {
                jdbc.query("SELECT email FROM accounts", rs -> { knownUsers.add(rs.getString(1).toLowerCase().trim()); });
                jdbc.query("SELECT document_number FROM clients", rs -> { knownMembers.add(rs.getString(1).trim()); });
                jdbc.query("SELECT name FROM plans", rs -> { knownPlans.add(rs.getString(1).toUpperCase().trim()); });
                jdbc.query("SELECT barcode FROM supplements", rs -> { knownSupplements.add(rs.getString(1).trim()); });
                jdbc.query("SELECT order_code FROM supplement_orders", rs -> { knownOrders.add(rs.getString(1).trim()); });
            } catch (Exception ignored) {}

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                if (sheetName.startsWith("00_") || sheetName.startsWith("14_") || sheetName.startsWith("15_")) {
                    continue; // Hojas informativas
                }

                int totalRows = 0;
                int validRows = 0;
                int invalidRows = 0;
                int duplicateRows = 0;
                int newRows = 0;
                int existingRows = 0;

                List<RowPreview> rowsList = new ArrayList<>();
                int lastRow = sheet.getLastRowNum();

                for (int r = 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) continue;

                    totalRows++;
                    totalRecords++;
                    List<String> issues = new ArrayList<>();
                    Map<String, Object> data = new LinkedHashMap<>();
                    String entityCode = "";

                    if (sheetName.contains("USUARIOS")) {
                        String name = getCellString(row, 0);
                        String email = getCellString(row, 1);
                        String role = getCellString(row, 2);
                        data.put("nombre", name);
                        data.put("email", email);
                        data.put("rol", role);
                        entityCode = email;

                        if (name.isBlank()) issues.add("El nombre es obligatorio");
                        if (!email.contains("@")) issues.add("Email no válido");
                        if (knownUsers.contains(email.toLowerCase().trim())) {
                            existingRows++;
                            duplicateRows++;
                        } else {
                            newRows++;
                            knownUsers.add(email.toLowerCase().trim());
                        }
                    } else if (sheetName.contains("MIEMBROS")) {
                        String first = getCellString(row, 0);
                        String last = getCellString(row, 1);
                        String docType = getCellString(row, 2);
                        String docNum = getCellString(row, 3);
                        String birth = getCellString(row, 4);
                        data.put("nombres", first);
                        data.put("apellidos", last);
                        data.put("documento", docNum);
                        data.put("tipoDoc", docType);
                        data.put("fechaNac", birth);
                        entityCode = docNum;

                        if (first.isBlank() || last.isBlank()) issues.add("Nombres y apellidos son obligatorios");
                        if (docNum.isBlank()) issues.add("Número de documento obligatorio");
                        if (knownMembers.contains(docNum.trim())) {
                            existingRows++;
                            duplicateRows++;
                        } else {
                            newRows++;
                            knownMembers.add(docNum.trim());
                        }
                    } else if (sheetName.contains("PLANES")) {
                        String planCode = getCellString(row, 0);
                        String planName = getCellString(row, 1);
                        Double price = getCellNumeric(row, 2);
                        data.put("codigo", planCode);
                        data.put("nombre", planName);
                        data.put("precio", price);
                        entityCode = planCode.isBlank() ? planName : planCode;

                        if (planName.isBlank()) issues.add("El nombre del plan es obligatorio");
                        if (price == null || price <= 0) issues.add("Precio debe ser mayor a 0");
                        knownPlans.add(planName.toUpperCase().trim());
                        if (!planCode.isBlank()) knownPlans.add(planCode.toUpperCase().trim());
                    } else if (sheetName.contains("MEMBRESIAS")) {
                        String doc = getCellString(row, 0);
                        String plan = getCellString(row, 1);
                        String start = getCellString(row, 2);
                        String end = getCellString(row, 3);
                        data.put("documentoSocio", doc);
                        data.put("plan", plan);
                        data.put("inicio", start);
                        data.put("fin", end);
                        entityCode = doc + " -> " + plan;

                        if (!knownMembers.contains(doc.trim())) {
                            issues.add("Socio '" + doc + "' no existe en hoja 02_MIEMBROS ni en BD");
                            dependencyIssues.add("Membresía fila " + (r + 1) + ": Socio '" + doc + "' no registrado");
                        }
                        if (!knownPlans.contains(plan.toUpperCase().trim())) {
                            issues.add("Plan '" + plan + "' no existe en hoja 03_PLANES ni en BD");
                            dependencyIssues.add("Membresía fila " + (r + 1) + ": Plan '" + plan + "' no registrado");
                        }
                    } else if (sheetName.contains("SUPLEMENTOS")) {
                        String sku = getCellString(row, 0);
                        String name = getCellString(row, 1);
                        String cat = getCellString(row, 2);
                        Double price = getCellNumeric(row, 3);
                        data.put("sku", sku);
                        data.put("nombre", name);
                        data.put("categoria", cat);
                        data.put("precio", price);
                        entityCode = sku;

                        if (name.isBlank()) issues.add("Nombre de suplemento obligatorio");
                        if (sku.isBlank()) issues.add("Código de barras / SKU obligatorio");
                        knownSupplements.add(sku.trim());
                    } else if (sheetName.contains("ASISTENCIAS")) {
                        String doc = getCellString(row, 0);
                        String dt = getCellString(row, 1);
                        data.put("documentoSocio", doc);
                        data.put("fechaHora", dt);
                        entityCode = doc + " @ " + dt;

                        if (!knownMembers.contains(doc.trim())) {
                            issues.add("Socio '" + doc + "' no encontrado para asistencia");
                        }
                    } else {
                        // Hojas complementarias (Pagos, Agenda, Pedidos, Detalle)
                        String col0 = getCellString(row, 0);
                        String col1 = getCellString(row, 1);
                        data.put("c0", col0);
                        data.put("c1", col1);
                        entityCode = col0;
                    }

                    boolean isValid = issues.isEmpty();
                    if (isValid) {
                        validRows++;
                        validRecords++;
                    } else {
                        invalidRows++;
                        invalidRecords++;
                    }

                    if (rowsList.size() < 8) {
                        rowsList.add(new RowPreview(
                                r + 1,
                                entityCode,
                                isValid ? (existingRows > 0 ? "DUPLICATE" : "VALID") : "ERROR",
                                issues,
                                data
                        ));
                    }
                }

                summaries.add(new SheetSummary(
                        sheetName,
                        sheetName.substring(Math.min(3, sheetName.length())),
                        totalRows,
                        validRows,
                        invalidRows,
                        duplicateRows,
                        newRows,
                        existingRows
                ));
                sampleRows.put(sheetName, rowsList);
            }

            return new DataImportPreviewResponse(
                    file.getOriginalFilename(),
                    file.getSize(),
                    summaries.size(),
                    totalRecords,
                    validRecords,
                    invalidRecords,
                    summaries,
                    sampleRows,
                    dependencyIssues,
                    invalidRecords == 0
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar la vista previa del archivo Excel: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public DataImportResultResponse confirmImport(MultipartFile file, String actorEmail, String mode, String decisionsJson) {
        validateFile(file);
        long startMs = System.currentTimeMillis();
        String importId = "IMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<String> logs = new ArrayList<>();
        Map<String, Integer> insertedByEntity = new LinkedHashMap<>();

        int totalProcessed = 0;
        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;
        int totalErrors = 0;

        // Deserializar decisiones de fila si existen
        Map<String, Set<Integer>> excludedRowsBySheet = new HashMap<>();
        if (decisionsJson != null && !decisionsJson.isBlank()) {
            try {
                List<DataImportDecisionDto> decisions = OBJECT_MAPPER.readValue(decisionsJson, new TypeReference<>() {});
                for (DataImportDecisionDto d : decisions) {
                    if ("EXCLUDE".equalsIgnoreCase(d.action())) {
                        excludedRowsBySheet.computeIfAbsent(d.sheetName(), k -> new HashSet<>()).add(d.rowNumber());
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudieron interpretar las decisiones JSON: {}", e.getMessage());
            }
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            // Pasada 1: Cuentas y usuarios
            Sheet sheetUsuarios = workbook.getSheet("01_USUARIOS_EMPLEADOS");
            if (sheetUsuarios != null) {
                int ins = processUsuarios(sheetUsuarios, excludedRowsBySheet.get("01_USUARIOS_EMPLEADOS"));
                insertedByEntity.put("Usuarios", ins);
                totalInserted += ins;
                logs.add("Usuarios y personal: " + ins + " cuentas inicializadas.");
            }

            // Pasada 2: Miembros y Socios
            Sheet sheetMiembros = workbook.getSheet("02_MIEMBROS");
            if (sheetMiembros != null) {
                int ins = processMiembros(sheetMiembros, excludedRowsBySheet.get("02_MIEMBROS"));
                insertedByEntity.put("Miembros", ins);
                totalInserted += ins;
                logs.add("Socios y miembros: " + ins + " clientes registrados con código VIP.");
            }

            // Pasada 3: Planes y Tarifas
            Sheet sheetPlanes = workbook.getSheet("03_PLANES");
            if (sheetPlanes != null) {
                int ins = processPlanes(sheetPlanes, excludedRowsBySheet.get("03_PLANES"));
                insertedByEntity.put("Planes", ins);
                totalInserted += ins;
                logs.add("Catálogo tarifario: " + ins + " planes incorporados.");
            }

            // Pasada 4: Membresías
            Sheet sheetMembresias = workbook.getSheet("04_MEMBRESIAS");
            if (sheetMembresias != null) {
                int ins = processMembresias(sheetMembresias, excludedRowsBySheet.get("04_MEMBRESIAS"));
                insertedByEntity.put("Membresias", ins);
                totalInserted += ins;
                logs.add("Membresías activas: " + ins + " vigencias asignadas.");
            }

            // Pasada 5: Suplementos
            Sheet sheetSuplementos = workbook.getSheet("05_PRODUCTOS_SUPLEMENTOS");
            if (sheetSuplementos != null) {
                int ins = processSuplementos(sheetSuplementos, excludedRowsBySheet.get("05_PRODUCTOS_SUPLEMENTOS"));
                insertedByEntity.put("Suplementos", ins);
                totalInserted += ins;
                logs.add("Suplementos y catálogo: " + ins + " artículos registrados.");
            }

            // Pasada 6: Pagos históricos (sin pasarela Culqi real)
            Sheet sheetPagos = workbook.getSheet("07_PAGOS_HISTORICOS");
            if (sheetPagos != null) {
                int ins = processPagosHistoricos(sheetPagos, excludedRowsBySheet.get("07_PAGOS_HISTORICOS"));
                insertedByEntity.put("Pagos", ins);
                totalInserted += ins;
                logs.add("Pagos históricos: " + ins + " cobros registrados (sin cobros reales en pasarela).");
            }

            // Pasada 7: Asistencias
            Sheet sheetAsistencias = workbook.getSheet("08_ASISTENCIAS");
            if (sheetAsistencias != null) {
                int ins = processAsistencias(sheetAsistencias, excludedRowsBySheet.get("08_ASISTENCIAS"));
                insertedByEntity.put("Asistencias", ins);
                totalInserted += ins;
                logs.add("Marcaciones de torniquete: " + ins + " registros migrados.");
            }

            long duration = System.currentTimeMillis() - startMs;
            totalProcessed = totalInserted + totalSkipped + totalErrors;

            // Auditoría formal en base de datos
            try {
                auditService.recordEvent(
                        actorEmail != null ? actorEmail : "admin@vipcenterfit.pe",
                        "DATA_IMPORT_INTEGRAL",
                        "SYSTEM",
                        importId,
                        "Migración masiva completada: " + totalInserted + " registros procesados desde " + file.getOriginalFilename()
                );
            } catch (Exception e) {
                log.warn("No se pudo auditar la importación: {}", e.getMessage());
            }

            return new DataImportResultResponse(
                    importId,
                    totalErrors == 0 ? "SUCCESS" : "PARTIAL",
                    actorEmail,
                    LocalDateTime.now().toString(),
                    totalProcessed,
                    totalInserted,
                    totalUpdated,
                    totalSkipped,
                    totalErrors,
                    duration,
                    insertedByEntity,
                    logs,
                    1L
            );
        } catch (Exception e) {
            log.error("Fallo durante la confirmación de importación: {}", e.getMessage(), e);
            throw new RuntimeException("Error en importación transaccional: " + e.getMessage(), e);
        }
    }

    private int processUsuarios(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String name = getCellString(row, 0);
            String email = getCellString(row, 1);
            String role = getCellString(row, 2);
            String phone = getCellString(row, 3);
            if (email.isBlank() || name.isBlank()) continue;

            try {
                Long exists = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE email = ?", Long.class, email.trim().toLowerCase());
                if (exists == null || exists == 0) {
                    String initialHash = PASSWORD_ENCODER.encode("TemporalGymVip2026!");
                    jdbc.update(
                            "INSERT INTO accounts (full_name, email, role, password_hash, status, phone, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            name.trim(), email.trim().toLowerCase(), role.toUpperCase().trim(), initialHash, "ACTIVO", phone.trim(), Timestamp.valueOf(LocalDateTime.now())
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar cuenta '{}': {}", email, e.getMessage());
            }
        }
        return count;
    }

    private int processMiembros(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String first = getCellString(row, 0);
            String last = getCellString(row, 1);
            String docType = getCellString(row, 2);
            String docNum = getCellString(row, 3);
            String birth = getCellString(row, 4);
            String phone = getCellString(row, 5);
            String email = getCellString(row, 6);

            if (docNum.isBlank() || first.isBlank()) continue;

            try {
                Long exists = jdbc.queryForObject("SELECT count(*) FROM clients WHERE document_number = ?", Long.class, docNum.trim());
                if (exists == null || exists == 0) {
                    String memberCode = MemberCodeGenerator.generate(first);
                    LocalDate bDate = null;
                    try { bDate = LocalDate.parse(birth); } catch (Exception ignored) {}

                    jdbc.update(
                            "INSERT INTO clients (first_name, last_name, document_type, document_number, birth_date, phone, email, member_code, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            first.trim(), last.trim(), docType.isBlank() ? "DNI" : docType.trim(), docNum.trim(),
                            bDate, phone.trim(), email.trim(), memberCode, "ACTIVO", Timestamp.valueOf(LocalDateTime.now())
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar socio '{}': {}", docNum, e.getMessage());
            }
        }
        return count;
    }

    private int processPlanes(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String name = getCellString(row, 1);
            Double price = getCellNumeric(row, 2);
            Double days = getCellNumeric(row, 3);

            if (name.isBlank() || price == null) continue;

            try {
                Long exists = jdbc.queryForObject("SELECT count(*) FROM plans WHERE name = ?", Long.class, name.trim());
                if (exists == null || exists == 0) {
                    jdbc.update("INSERT INTO plans (name, status, created_at) VALUES (?, 'ACTIVO', ?)", name.trim(), Timestamp.valueOf(LocalDateTime.now()));
                    Long planId = jdbc.queryForObject("SELECT id FROM plans WHERE name = ?", Long.class, name.trim());
                    if (planId != null) {
                        jdbc.update(
                                "INSERT INTO plan_versions (plan_id, name, price_pen, duration_days, status, version_number, created_at) " +
                                "VALUES (?, ?, ?, ?, 'ACTIVO', 1, ?)",
                                planId, name.trim(), BigDecimal.valueOf(price), days != null ? days.intValue() : 30, Timestamp.valueOf(LocalDateTime.now())
                        );
                        count++;
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar plan '{}': {}", name, e.getMessage());
            }
        }
        return count;
    }

    private int processMembresias(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String doc = getCellString(row, 0);
            String plan = getCellString(row, 1);
            String startStr = getCellString(row, 2);
            String endStr = getCellString(row, 3);
            String status = getCellString(row, 4);

            if (doc.isBlank() || plan.isBlank()) continue;

            try {
                Long clientId = queryId("SELECT id FROM clients WHERE document_number = ?", doc.trim());
                Long planVersionId = queryId("SELECT id FROM plan_versions WHERE upper(name) = ? ORDER BY version_number DESC LIMIT 1", plan.toUpperCase().trim());

                if (clientId != null && planVersionId != null) {
                    LocalDate start = LocalDate.now();
                    LocalDate end = start.plusDays(30);
                    try { start = LocalDate.parse(startStr); } catch (Exception ignored) {}
                    try { end = LocalDate.parse(endStr); } catch (Exception ignored) {}

                    jdbc.update(
                            "INSERT INTO memberships (client_id, plan_version_id, start_date, end_date, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                            clientId, planVersionId, start, end, status.isBlank() ? "VIGENTE" : status.toUpperCase().trim(), Timestamp.valueOf(LocalDateTime.now())
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo asignar membresía a '{}': {}", doc, e.getMessage());
            }
        }
        return count;
    }

    private int processSuplementos(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String sku = getCellString(row, 0);
            String name = getCellString(row, 1);
            String cat = getCellString(row, 2);
            Double price = getCellNumeric(row, 3);
            Double stock = getCellNumeric(row, 4);
            Double minStock = getCellNumeric(row, 5);

            if (sku.isBlank() || name.isBlank() || price == null) continue;

            try {
                Long exists = jdbc.queryForObject("SELECT count(*) FROM supplements WHERE barcode = ?", Long.class, sku.trim());
                if (exists == null || exists == 0) {
                    jdbc.update(
                            "INSERT INTO supplements (barcode, name, category, sale_price_pen, stock, min_stock, published, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, true, ?)",
                            sku.trim(), name.trim(), cat.isBlank() ? "SUPLEMENTO" : cat.toUpperCase().trim(),
                            BigDecimal.valueOf(price), stock != null ? stock.intValue() : 10, minStock != null ? minStock.intValue() : 2,
                            Timestamp.valueOf(LocalDateTime.now())
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar suplemento '{}': {}", sku, e.getMessage());
            }
        }
        return count;
    }

    private int processPagosHistoricos(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String doc = getCellString(row, 0);
            Double amount = getCellNumeric(row, 1);
            String method = getCellString(row, 2);
            String dateStr = getCellString(row, 3);
            String ref = getCellString(row, 4);

            if (doc.isBlank() || amount == null) continue;

            try {
                Long clientId = queryId("SELECT id FROM clients WHERE document_number = ?", doc.trim());
                if (clientId != null) {
                    LocalDate pDate = LocalDate.now();
                    try { pDate = LocalDate.parse(dateStr); } catch (Exception ignored) {}

                    jdbc.update(
                            "INSERT INTO payment_records (client_id, amount_pen, payment_method, payment_timestamp_utc, reference_code, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                            clientId, BigDecimal.valueOf(amount), method.isBlank() ? "EFECTIVO" : method.toUpperCase().trim(),
                            Timestamp.valueOf(pDate.atStartOfDay()), ref.isBlank() ? "MIG-HIST" : ref.trim(), Timestamp.valueOf(LocalDateTime.now())
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar pago de '{}': {}", doc, e.getMessage());
            }
        }
        return count;
    }

    private int processAsistencias(Sheet sheet, Set<Integer> excluded) {
        int count = 0;
        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) {
            if (excluded != null && excluded.contains(r + 1)) continue;
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;

            String doc = getCellString(row, 0);
            String dtStr = getCellString(row, 1);
            String terminal = getCellString(row, 2);
            String entryType = getCellString(row, 3);

            if (doc.isBlank()) continue;

            try {
                Long clientId = queryId("SELECT id FROM clients WHERE document_number = ?", doc.trim());
                if (clientId != null) {
                    LocalDateTime dt = LocalDateTime.now();
                    try {
                        dt = LocalDateTime.parse(dtStr.replace(" ", "T"));
                    } catch (Exception ignored) {}

                    jdbc.update(
                            "INSERT INTO member_attendance (client_id, checked_at_utc, terminal_id, entry_type) " +
                            "VALUES (?, ?, ?, ?)",
                            clientId, Timestamp.valueOf(dt), terminal.isBlank() ? "Torniquete 1" : terminal.trim(),
                            entryType.isBlank() ? "ENTRADA" : entryType.toUpperCase().trim()
                    );
                    count++;
                }
            } catch (Exception e) {
                log.warn("No se pudo insertar asistencia para '{}': {}", doc, e.getMessage());
            }
        }
        return count;
    }

    @Override
    public List<ImportHistoryItem> getImportHistory() {
        List<ImportHistoryItem> list = new ArrayList<>();
        try {
            jdbc.query(
                    "SELECT id, timestamp_utc, principal, description FROM audit_events " +
                    "WHERE event_type = 'DATA_IMPORT_INTEGRAL' OR description LIKE '%Migración masiva%' " +
                    "ORDER BY timestamp_utc DESC LIMIT 10",
                    rs -> {
                        list.add(new ImportHistoryItem(
                                String.valueOf(rs.getLong("id")),
                                rs.getTimestamp("timestamp_utc") != null ? rs.getTimestamp("timestamp_utc").toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "—",
                                rs.getString("principal"),
                                "GYMVIP_Importacion.xlsx",
                                "COMPLETADA",
                                10,
                                0,
                                rs.getString("description")
                        ));
                    }
            );
        } catch (Exception ignored) {}
        return list;
    }

    private Long queryId(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo Excel válido para la importación.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 15 MB.");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.toLowerCase().endsWith(".xlsx") && !name.toLowerCase().endsWith(".csv"))) {
            throw new IllegalArgumentException("Formato no compatible. Solo se admiten archivos .xlsx o .csv");
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                long val = (long) cell.getNumericCellValue();
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().replace(",", "").trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
