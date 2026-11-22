package com.gymvip.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final JdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Snapshot(LocalDate from, LocalDate to, Map<String, BigDecimal> metrics,
                           Map<String, Long> memberRegistrationsByDay, Map<String, Long> memberRegistrationsByMonth) {}

    public static List<String> selectedMetrics(String csv) {
        if (csv == null || csv.isBlank()) return ORDER;
        Set<String> requested = new java.util.HashSet<>(List.of(csv.split(",", -1)));
        if (requested.contains("") || requested.stream().anyMatch(key -> !LABELS.containsKey(key)))
            throw new IllegalArgumentException("Metrica de reporte desconocida.");
        return ORDER.stream().filter(requested::contains).toList();
    }

    public Snapshot snapshot(LocalDate from, LocalDate to, String csv) {
        if (from == null || to == null || from.isAfter(to) || from.plusYears(1).isBefore(to))
            throw new IllegalArgumentException("Selecciona un rango valido de hasta un ano.");
        var selected = selectedMetrics(csv);
        LocalDateTime start = from.atStartOfDay(ZoneId.of("America/Lima")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String key : selected) {
            BigDecimal value = switch (key) {
                case "memberRegistrations" -> scalar("SELECT count(*) FROM clients WHERE created_at>=? AND created_at<?", start, end);
                case "attendance" -> scalar("SELECT count(*) FROM member_attendance WHERE checked_at_utc>=? AND checked_at_utc<?", start, end);
                case "plansAssigned" -> scalar("SELECT count(*) FROM membership_periods WHERE created_at>=? AND created_at<?", start, end);
                case "expirations" -> scalar("SELECT count(*) FROM memberships WHERE end_date>=? AND end_date<=? AND status='VIGENTE'", from, to);
                case "membershipPaymentsPen" -> scalar("SELECT coalesce(sum(amount_pen),0) FROM payment_records WHERE payment_timestamp_utc>=? AND payment_timestamp_utc<?", start, end);
                case "supplementOrders" -> scalar("SELECT count(*) FROM supplement_orders WHERE created_at_utc>=? AND created_at_utc<?", start, end);
                case "supplementUnits" -> scalar("SELECT coalesce(sum(i.quantity),0) FROM supplement_order_items i JOIN supplement_orders o ON o.id=i.order_id WHERE o.verified_at_utc>=? AND o.verified_at_utc<? AND o.payment_status='VERIFICADO'", start, end);
                case "lowStock" -> scalar("SELECT count(*) FROM supplements WHERE stock<=min_stock AND published=true");
                case "promotions" -> scalar("SELECT count(*) FROM supplement_promotions WHERE created_at_utc>=? AND created_at_utc<?", start, end);
                case "announcements" -> scalar("SELECT count(*) FROM gym_announcements WHERE created_at_utc>=? AND created_at_utc<? AND is_active=true", start, end);
                default -> throw new IllegalArgumentException("Metrica no permitida.");
            };
            values.put(key, value);
        }
        Map<String, Long> days = new LinkedHashMap<>();
        Map<String, Long> months = new LinkedHashMap<>();
        if (selected.contains("memberRegistrations")) {
            jdbc.query("SELECT created_at FROM clients WHERE created_at>=? AND created_at<? ORDER BY created_at",
                    rs -> {
                        LocalDate day = rs.getTimestamp(1).toLocalDateTime().atZone(ZoneId.of("UTC"))
                                .withZoneSameInstant(ZoneId.of("America/Lima")).toLocalDate();
                        days.merge(day.toString(), 1L, Long::sum);
                        months.merge(day.getYear() + "-" + String.format("%02d", day.getMonthValue()), 1L, Long::sum);
                    }, Timestamp.valueOf(start), Timestamp.valueOf(end));
        }
        return new Snapshot(from, to, values, days, months);
    }

    private BigDecimal scalar(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
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
                stream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                stream.newLineAtOffset(48, 750);
                stream.showText("GYM VIP - Reporte operativo");
                stream.setFont(PDType1Font.HELVETICA, 11);
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
