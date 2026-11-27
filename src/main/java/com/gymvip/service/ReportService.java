package com.gymvip.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {
    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("memberRegistrations", "Socios registrados"),
            Map.entry("attendance", "Asistencias"),
            Map.entry("plansAssigned", "Planes asignados"),
            Map.entry("expirations", "Membresias por vencer"),
            Map.entry("membershipPaymentsPen", "Cobros membresias PEN"),
            Map.entry("supplementOrders", "Pedidos suplementos"),
            Map.entry("supplementUnits", "Unidades vendidas"),
            Map.entry("lowStock", "Suplementos con stock bajo"),
            Map.entry("promotions", "Promociones creadas"),
            Map.entry("announcements", "Avisos publicados")
    );
    private static final List<String> ORDER = List.of("memberRegistrations", "attendance", "plansAssigned",
            "expirations", "membershipPaymentsPen", "supplementOrders", "supplementUnits", "lowStock",
            "promotions", "announcements");
    private static final String IN_SCOPE = """
            b.organization_id = :organizationId AND (CAST(:branchId AS uuid) IS NULL OR b.id = :branchId)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public record Scope(UUID organizationId, UUID branchId) {}
    public record Snapshot(LocalDate from, LocalDate to, Map<String, BigDecimal> metrics,
                           Map<String, Long> memberRegistrationsByDay, Map<String, Long> memberRegistrationsByMonth) {}

    public static List<String> selectedMetrics(String csv) {
        if (csv == null || csv.isBlank()) return ORDER;
        Set<String> requested = new java.util.HashSet<>(List.of(csv.split(",", -1)));
        if (requested.contains("") || requested.stream().anyMatch(key -> !LABELS.containsKey(key)))
            throw new IllegalArgumentException("Metrica de reporte desconocida.");
        return ORDER.stream().filter(requested::contains).toList();
    }

    public Snapshot snapshot(LocalDate from, LocalDate to, String csv, Scope scope) {
        if (from == null || to == null || from.isAfter(to) || from.plusYears(1).isBefore(to))
            throw new IllegalArgumentException("Selecciona un rango valido de hasta un ano.");
        var selected = selectedMetrics(csv);
        LocalDateTime start = from.atStartOfDay(ZoneId.of("America/Lima")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        var parameters = new MapSqlParameterSource()
                .addValue("organizationId", scope.organizationId())
                .addValue("branchId", scope.branchId())
                .addValue("from", from)
                .addValue("to", to)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("startInstant", start.atOffset(java.time.ZoneOffset.UTC))
                .addValue("endInstant", end.atOffset(java.time.ZoneOffset.UTC));
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String key : selected) {
            String sql = switch (key) {
                case "memberRegistrations" -> """
                        SELECT count(*) FROM clients c JOIN branches b ON b.id=c.branch_id
                        WHERE c.created_at>=:start AND c.created_at<:end
                        """;
                case "attendance" -> """
                        SELECT count(*) FROM member_attendance a JOIN clients c ON c.id=a.client_id
                        JOIN branches b ON b.id=c.branch_id
                        WHERE a.checked_at_utc>=:start AND a.checked_at_utc<:end
                        """;
                case "plansAssigned" -> """
                        SELECT count(*) FROM membership_periods p JOIN memberships m ON m.id=p.membership_id
                        JOIN clients c ON c.id=m.client_id JOIN branches b ON b.id=c.branch_id
                        WHERE p.created_at>=:start AND p.created_at<:end
                        """;
                case "expirations" -> """
                        SELECT count(*) FROM memberships m JOIN clients c ON c.id=m.client_id
                        JOIN branches b ON b.id=c.branch_id
                        WHERE m.end_date>=:from AND m.end_date<=:to AND m.status='VIGENTE'
                        """;
                case "membershipPaymentsPen" -> """
                        SELECT COALESCE(sum(p.amount_pen),0) FROM payment_records p
                        JOIN memberships m ON m.id=p.membership_id JOIN clients c ON c.id=m.client_id
                        JOIN branches b ON b.id=c.branch_id
                        WHERE p.payment_timestamp_utc>=:start AND p.payment_timestamp_utc<:end
                        """;
                case "supplementOrders" -> """
                        SELECT count(*) FROM supplement_orders o JOIN branches b ON b.id=o.branch_id
                        WHERE o.created_at_utc>=:startInstant AND o.created_at_utc<:endInstant
                        """;
                case "supplementUnits" -> """
                        SELECT COALESCE(sum(i.quantity),0) FROM supplement_order_items i
                        JOIN supplement_orders o ON o.id=i.order_id JOIN branches b ON b.id=o.branch_id
                        WHERE o.verified_at_utc>=:startInstant AND o.verified_at_utc<:endInstant AND o.payment_status='VERIFICADO'
                        """;
                case "lowStock" -> """
                        SELECT count(*) FROM supplements s JOIN branches b ON b.id=s.branch_id
                        WHERE s.stock<=s.min_stock AND s.published=true
                        """;
                case "promotions" -> """
                        SELECT count(*) FROM supplement_promotions p JOIN supplements s ON s.id=p.supplement_id
                        JOIN branches b ON b.id=s.branch_id
                        WHERE p.created_at_utc>=:startInstant AND p.created_at_utc<:endInstant
                        """;
                case "announcements" -> """
                        SELECT count(*) FROM gym_announcements a JOIN gym_services s ON s.id=a.facility_id
                        JOIN branches b ON b.id=s.branch_id
                        WHERE a.created_at_utc>=:startInstant AND a.created_at_utc<:endInstant AND a.is_active=true
                        """;
                default -> throw new IllegalArgumentException("Metrica no permitida.");
            };
            BigDecimal value = jdbc.queryForObject(sql + " AND " + IN_SCOPE, parameters, BigDecimal.class);
            values.put(key, value == null ? BigDecimal.ZERO : value);
        }
        Map<String, Long> days = new LinkedHashMap<>();
        Map<String, Long> months = new LinkedHashMap<>();
        if (selected.contains("memberRegistrations")) {
            jdbc.query("""
                    SELECT c.created_at FROM clients c JOIN branches b ON b.id=c.branch_id
                    WHERE c.created_at>=:start AND c.created_at<:end AND
                    """ + IN_SCOPE + " ORDER BY c.created_at", parameters,
                    rs -> {
                        LocalDate day = rs.getTimestamp(1).toLocalDateTime().atZone(ZoneId.of("UTC"))
                                .withZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate();
                        days.merge(day.toString(), 1L, Long::sum);
                        months.merge(day.getYear() + "-" + String.format("%02d", day.getMonthValue()), 1L, Long::sum);
                    });
        }
        return new Snapshot(from, to, values, days, months);
    }

    public byte[] xlsx(Snapshot snapshot) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Metricas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("GYM VIP: " + snapshot.from() + " a " + snapshot.to());
            int rowIndex = 1;
            for (var entry : snapshot.metrics().entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(LABELS.get(entry.getKey()));
                row.createCell(1).setCellValue(entry.getValue().doubleValue());
            }
            if (!snapshot.memberRegistrationsByDay().isEmpty()) {
                Sheet daily = workbook.createSheet("Altas por dia");
                int i = 0;
                for (var entry : snapshot.memberRegistrationsByDay().entrySet()) {
                    Row row = daily.createRow(i++);
                    row.createCell(0).setCellValue(entry.getKey());
                    row.createCell(1).setCellValue(entry.getValue());
                }
                Sheet monthly = workbook.createSheet("Altas por mes");
                i = 0;
                for (var entry : snapshot.memberRegistrationsByMonth().entrySet()) {
                    Row row = monthly.createRow(i++);
                    row.createCell(0).setCellValue(entry.getKey());
                    row.createCell(1).setCellValue(entry.getValue());
                }
            }
            sheet.autoSizeColumn(0);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] pdf(Snapshot snapshot) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(48, 750);
                stream.showText("GYM VIP - Reporte operativo");
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(0, -28);
                stream.showText(snapshot.from() + " a " + snapshot.to() + " (America/Lima)");
                for (var entry : snapshot.metrics().entrySet()) {
                    stream.newLineAtOffset(0, -24);
                    stream.showText(LABELS.get(entry.getKey()) + ": " + entry.getValue().toPlainString());
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
