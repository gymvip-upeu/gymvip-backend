package com.gymvip.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Servicio generador de las plantillas oficiales Excel multi-hoja para migración integral de datos (Fase 5 & 6).
 * Produce 3 variantes: Plantilla Vacía, Datos Demo sintéticos y Ejemplo Integral documentado.
 */
@Service
public class DataImportTemplateService {

    public enum TemplateVariant {
        EMPTY,
        DEMO,
        EXAMPLE
    }

    public byte[] generateWorkbook(TemplateVariant variant) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Estilos globales Luxury Dark & Gold
            CellStyle goldHeaderStyle = workbook.createCellStyle();
            Font fontHeader = workbook.createFont();
            fontHeader.setBold(true);
            fontHeader.setColor(IndexedColors.WHITE.getIndex());
            goldHeaderStyle.setFont(fontHeader);
            goldHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            goldHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            goldHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            goldHeaderStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle textStyle = workbook.createCellStyle();
            DataFormat fmt = workbook.createDataFormat();
            textStyle.setDataFormat(fmt.getFormat("@"));

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(fmt.getFormat("#,##0.00"));

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(fmt.getFormat("yyyy-mm-dd"));

            // 00_INSTRUCCIONES
            createInstructionsSheet(workbook, variant, goldHeaderStyle);

            // 01_USUARIOS_EMPLEADOS
            createUsuariosSheet(workbook, variant, goldHeaderStyle, textStyle);

            // 02_MIEMBROS
            createMiembrosSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle);

            // 03_PLANES
            createPlanesSheet(workbook, variant, goldHeaderStyle, textStyle, numberStyle);

            // 04_MEMBRESIAS
            createMembresiasSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle, numberStyle);

            // 05_PRODUCTOS_SUPLEMENTOS
            createSuplementosSheet(workbook, variant, goldHeaderStyle, textStyle, numberStyle);

            // 06_PROMOCIONES
            createPromocionesSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle, numberStyle);

            // 07_PAGOS_HISTORICOS
            createPagosSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle, numberStyle);

            // 08_ASISTENCIAS
            createAsistenciasSheet(workbook, variant, goldHeaderStyle, textStyle);

            // 09_AGENDA
            createAgendaSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle);

            // 10_PEDIDOS
            createPedidosSheet(workbook, variant, goldHeaderStyle, textStyle, dateStyle, numberStyle);

            // 11_DETALLE_PEDIDOS
            createDetallePedidosSheet(workbook, variant, goldHeaderStyle, textStyle, numberStyle);

            // 14_CATALOGOS_VALIDOS
            createCatalogosValidosSheet(workbook, goldHeaderStyle);

            // 15_DICCIONARIO_DE_DATOS
            createDiccionarioSheet(workbook, goldHeaderStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error al generar la plantilla Excel multi-hoja GYM VIP", e);
        }
    }

    private void createInstructionsSheet(Workbook wb, TemplateVariant variant, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("00_INSTRUCCIONES");
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("GYM VIP / VIP CENTER FIT — PLANTILLA DE IMPORTACIÓN INTEGRAL DE DATOS");
        r0.getCell(0).setCellStyle(headerStyle);

        Row r2 = sheet.createRow(2);
        r2.createCell(0).setCellValue("Variante de la Plantilla: " + variant.name());
        Row r3 = sheet.createRow(3);
        r3.createCell(0).setCellValue("Fecha de Emisión: 2026-11-24");
        Row r4 = sheet.createRow(4);
        r4.createCell(0).setCellValue("Versión Oficial: 1.0.0-PROD");

        String[] instructions = {
                "1. NO modifique los nombres de las hojas ni el orden de los encabezados de columnas.",
                "2. Los campos identificadores como DNI, teléfonos y códigos de barra están formateados como TEXTO para preservar ceros iniciales.",
                "3. Las contraseñas NO deben incluirse en texto plano; las cuentas de usuarios se crean inactivas con invitación por correo.",
                "4. Los importes monetarios deben expresarse en Soles Peruanos (PEN) sin el símbolo 'S/'.",
                "5. Las fechas deben registrarse obligatoriamente en formato ISO 'YYYY-MM-DD' (ej. 2026-11-24).",
                "6. Las dependencias deben respetarse: un socio debe existir en '02_MIEMBROS' antes de registrar su membresía o asistencia.",
                "7. Durante la importación de datos históricos NO se emitirán cobros reales en pasarelas de pago ni envíos masivos de WhatsApp.",
                "8. Consulte la hoja '14_CATALOGOS_VALIDOS' para verificar valores admitidos en roles, estados y métodos de pago.",
                "9. Consulte la hoja '15_DICCIONARIO_DE_DATOS' para revisar restricciones de longitud y campos obligatorios."
        };

        for (int i = 0; i < instructions.length; i++) {
            Row r = sheet.createRow(6 + i);
            r.createCell(0).setCellValue(instructions[i]);
        }
        sheet.setColumnWidth(0, 24000);
    }

    private void createUsuariosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle) {
        Sheet sheet = wb.createSheet("01_USUARIOS_EMPLEADOS");
        String[] headers = {"Nombre Completo*", "Correo Electrónico*", "Rol Principal* (ADMIN/RECEPCION/AUDITOR)", "Teléfono", "Estado (ACTIVO/INACTIVO)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(3, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"Juan Pérez Quispe", "recepcion.central@vipcenterfit.pe", "RECEPCION", "951234567", "ACTIVO"},
                    {"María Morales Soto", "auditor.operativo@vipcenterfit.pe", "AUDITOR", "952345678", "ACTIVO"}
            };
            populateDataRows(sheet, data, null, null, null);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createMiembrosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle) {
        Sheet sheet = wb.createSheet("02_MIEMBROS");
        String[] headers = {"Nombres*", "Apellidos*", "Tipo Documento* (DNI/CE/PASAPORTE)", "Número Documento*", "Fecha Nacimiento* (YYYY-MM-DD)", "Teléfono", "Correo Electrónico", "País (PE)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(3, textStyle);
        sheet.setDefaultColumnStyle(5, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"Carlos Alberto", "Condori Mamani", "DNI", "70123456", "1995-04-12", "951888111", "carlos.condori@gmail.com", "PE"},
                    {"Lucía Elena", "Apaza Flores", "DNI", "72456789", "1998-09-23", "952777222", "lucia.apaza@hotmail.com", "PE"},
                    {"Fernando David", "Vargas Ramos", "DNI", "73987654", "2000-01-15", "953666333", "fernando.vargas@outlook.com", "PE"}
            };
            populateDataRows(sheet, data, null, null, null);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createPlanesSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("03_PLANES");
        String[] headers = {"Código Plan*", "Nombre del Plan*", "Precio Soles PEN*", "Duración Días*", "Estado (ACTIVO/INACTIVO)", "Descripción"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"PLAN-VIP-MES", "Plan VIP Mensual", 120.00, 30, "ACTIVO", "Acceso total a sala de pesas y cardio"},
                    {"PLAN-VIP-TRI", "Plan VIP Trimestral", 320.00, 90, "ACTIVO", "Acceso total con 10% de descuento"},
                    {"PLAN-VIP-ANUAL", "Plan VIP Black Anual", 1100.00, 365, "ACTIVO", "Membresía anual completa con evaluación inicial"}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createMembresiasSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("04_MEMBRESIAS");
        String[] headers = {"Número Documento Socio*", "Código Plan*", "Fecha Inicio* (YYYY-MM-DD)", "Fecha Fin* (YYYY-MM-DD)", "Estado* (VIGENTE/VENCIDO/CONGELADO)", "Monto Pagado PEN*"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);
        sheet.setDefaultColumnStyle(1, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"70123456", "PLAN-VIP-MES", "2026-11-01", "2026-12-01", "VIGENTE", 120.00},
                    {"72456789", "PLAN-VIP-TRI", "2026-10-15", "2027-01-15", "VIGENTE", 320.00},
                    {"73987654", "PLAN-VIP-MES", "2026-10-01", "2026-11-01", "VENCIDO", 120.00}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createSuplementosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("05_PRODUCTOS_SUPLEMENTOS");
        String[] headers = {"Código Barras / SKU*", "Nombre del Suplemento*", "Categoría* (PROTEINA/CREATINA/AMINOACIDOS/BEBIDAS)", "Precio Venta PEN*", "Stock Actual*", "Stock Mínimo*", "Publicado (SI/NO)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"7750011223344", "Whey Protein 100% Gold 2kg", "PROTEINA", 240.00, 15, 3, "SI"},
                    {"7750011223355", "Creatina Creapure 300g Micronizada", "CREATINA", 110.00, 22, 5, "SI"},
                    {"7750011223366", "BCAA 2:1:1 Recuperación 500g", "AMINOACIDOS", 95.00, 8, 2, "SI"},
                    {"7750011223377", "Bebida Isotónica 500ml Electrolitos", "BEBIDAS", 6.50, 45, 10, "SI"}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createPromocionesSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("06_PROMOCIONES");
        String[] headers = {"Nombre Promoción*", "Código Barras Producto*", "Porcentaje Descuento*", "Fecha Inicio (YYYY-MM-DD)", "Fecha Fin (YYYY-MM-DD)", "Activa (SI/NO)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(1, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"Black Week Proteínas", "7750011223344", 15.00, "2026-11-20", "2026-11-30", "SI"},
                    {"Promo Creatina Primavera", "7750011223355", 10.00, "2026-11-01", "2026-11-30", "SI"}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createPagosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("07_PAGOS_HISTORICOS");
        String[] headers = {"Número Documento Socio*", "Monto PEN*", "Método Pago* (EFECTIVO/TARJETA/TRANSFERENCIA/YAPE/PLIN)", "Fecha Pago* (YYYY-MM-DD)", "Referencia / N° Operación"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);
        sheet.setDefaultColumnStyle(4, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"70123456", 120.00, "YAPE", "2026-11-01", "OP-YAPE-98213"},
                    {"72456789", 320.00, "TARJETA", "2026-10-15", "VISA-TRANS-4412"}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createAsistenciasSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle) {
        Sheet sheet = wb.createSheet("08_ASISTENCIAS");
        String[] headers = {"Número Documento Socio*", "Fecha y Hora* (YYYY-MM-DD HH:MM)", "Torniquete o Terminal*", "Sentido (ENTRADA/SALIDA)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);
        sheet.setDefaultColumnStyle(1, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"70123456", "2026-11-23 07:15", "Torniquete Principal 1", "ENTRADA"},
                    {"72456789", "2026-11-23 08:30", "Torniquete Principal 2", "ENTRADA"}
            };
            populateDataRows(sheet, data, null, null, null);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createAgendaSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle) {
        Sheet sheet = wb.createSheet("09_AGENDA");
        String[] headers = {"Número Documento Socio*", "Fecha Reserva* (YYYY-MM-DD)", "Hora Inicio* (HH:MM)", "Servicio o Sala*", "Estado (CONFIRMADA/CANCELADA)"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"70123456", "2026-11-25", "08:00", "Sala Spinning VIP", "CONFIRMADA"},
                    {"72456789", "2026-11-25", "10:00", "Evaluación Física InBody", "CONFIRMADA"}
            };
            populateDataRows(sheet, data, null, null, null);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createPedidosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle dateStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("10_PEDIDOS");
        String[] headers = {"Código Pedido*", "Número Documento Socio*", "Fecha Pedido* (YYYY-MM-DD)", "Estado Pago* (PENDIENTE/VERIFICADO)", "Estado Entrega* (PENDIENTE/ENTREGADO)", "Monto Total PEN*"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);
        sheet.setDefaultColumnStyle(1, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"PED-2026-001", "70123456", "2026-11-22", "VERIFICADO", "ENTREGADO", 240.00},
                    {"PED-2026-002", "72456789", "2026-11-23", "VERIFICADO", "PENDIENTE", 110.00}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createDetallePedidosSheet(Workbook wb, TemplateVariant variant, CellStyle hStyle, CellStyle textStyle, CellStyle numStyle) {
        Sheet sheet = wb.createSheet("11_DETALLE_PEDIDOS");
        String[] headers = {"Código Pedido*", "Código Barras Producto*", "Cantidad*", "Precio Unitario PEN*", "Subtotal PEN*"};
        createHeaderRow(sheet, headers, hStyle);
        sheet.setDefaultColumnStyle(0, textStyle);
        sheet.setDefaultColumnStyle(1, textStyle);

        if (variant != TemplateVariant.EMPTY) {
            Object[][] data = {
                    {"PED-2026-001", "7750011223344", 1, 240.00, 240.00},
                    {"PED-2026-002", "7750011223355", 1, 110.00, 110.00}
            };
            populateDataRows(sheet, data, null, null, numStyle);
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createCatalogosValidosSheet(Workbook wb, CellStyle hStyle) {
        Sheet sheet = wb.createSheet("14_CATALOGOS_VALIDOS");
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("CATÁLOGOS OFICIALES Y ENUMS PERMITIDOS EN GYM VIP");
        r0.getCell(0).setCellStyle(hStyle);

        String[][] catalogs = {
                {"Roles de Usuario", "ADMIN", "RECEPCION", "AUDITOR"},
                {"Tipos de Documento", "DNI (8 dígitos)", "CE (Carné de Extranjería)", "PASAPORTE"},
                {"Estados de Membresía", "VIGENTE", "VENCIDO", "CONGELADO", "CANCELADO"},
                {"Categorías Suplemento", "PROTEINA", "CREATINA", "AMINOACIDOS", "PREENTRENO", "BEBIDAS", "SNACKS"},
                {"Métodos de Pago", "EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE", "PLIN"},
                {"Estados de Pedido", "PENDIENTE", "VERIFICADO", "ENTREGADO", "CANCELADO"}
        };

        int rowIdx = 2;
        for (String[] cat : catalogs) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(cat[0]);
            r.getCell(0).getCellStyle().setFont(wb.createFont());
            for (int j = 1; j < cat.length; j++) {
                r.createCell(j).setCellValue(cat[j]);
            }
        }
        autoSizeColumns(sheet, 6);
    }

    private void createDiccionarioSheet(Workbook wb, CellStyle hStyle) {
        Sheet sheet = wb.createSheet("15_DICCIONARIO_DE_DATOS");
        String[] headers = {"Hoja", "Campo", "Descripción", "Tipo de Dato", "Obligatorio", "Restricción / Formato", "Entidad Destino"};
        createHeaderRow(sheet, headers, hStyle);

        String[][] dict = {
                {"01_USUARIOS_EMPLEADOS", "Nombre Completo", "Nombre y apellidos del colaborador", "Texto", "SÍ", "Máx 150 caracteres", "accounts"},
                {"01_USUARIOS_EMPLEADOS", "Correo Electrónico", "Email único de acceso al sistema", "Texto / Email", "SÍ", "Formato email válido", "accounts"},
                {"01_USUARIOS_EMPLEADOS", "Rol Principal", "Nivel de privilegios RBAC", "Texto / Enum", "SÍ", "ADMIN, RECEPCION, AUDITOR", "accounts"},
                {"02_MIEMBROS", "Número Documento", "DNI o documento de identidad", "Texto", "SÍ", "8 dígitos para DNI, único", "clients"},
                {"02_MIEMBROS", "Nombres", "Nombres del socio", "Texto", "SÍ", "Máx 100 caracteres", "clients"},
                {"02_MIEMBROS", "Apellidos", "Apellidos del socio", "Texto", "SÍ", "Máx 100 caracteres", "clients"},
                {"03_PLANES", "Código Plan", "Identificador único comercial", "Texto", "SÍ", "Alfanumérico (ej. PLAN-VIP-MES)", "plans"},
                {"03_PLANES", "Precio Soles PEN", "Tarifa oficial en PEN", "Decimal", "SÍ", "Mayor a 0.00", "plans"},
                {"04_MEMBRESIAS", "Número Documento Socio", "Referencia al socio", "Texto", "SÍ", "Debe existir en 02_MIEMBROS", "memberships"},
                {"04_MEMBRESIAS", "Código Plan", "Referencia al plan", "Texto", "SÍ", "Debe existir en 03_PLANES", "memberships"},
                {"05_PRODUCTOS_SUPLEMENTOS", "Código Barras / SKU", "Código de barras único", "Texto", "SÍ", "8 a 14 dígitos", "supplements"},
                {"07_PAGOS_HISTORICOS", "Monto PEN", "Importe cobrado en Soles", "Decimal", "SÍ", "Mayor a 0.00", "payment_records"},
                {"08_ASISTENCIAS", "Fecha y Hora", "Timestamp de marcación", "Fecha/Hora", "SÍ", "YYYY-MM-DD HH:MM", "member_attendance"},
                {"10_PEDIDOS", "Código Pedido", "Código único del pedido", "Texto", "SÍ", "Alfanumérico único", "supplement_orders"}
        };

        populateDataRows(sheet, dict, null, null, null);
        autoSizeColumns(sheet, headers.length);
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private void populateDataRows(Sheet sheet, Object[][] data, CellStyle textStyle, CellStyle dateStyle, CellStyle numStyle) {
        int rowIdx = sheet.getLastRowNum() + 1;
        for (Object[] rowData : data) {
            Row row = sheet.createRow(rowIdx++);
            for (int colIdx = 0; colIdx < rowData.length; colIdx++) {
                Cell cell = row.createCell(colIdx);
                Object val = rowData[colIdx];
                if (val instanceof Number num) {
                    cell.setCellValue(num.doubleValue());
                    if (numStyle != null) cell.setCellStyle(numStyle);
                } else if (val != null) {
                    cell.setCellValue(val.toString());
                    if (textStyle != null) cell.setCellStyle(textStyle);
                }
            }
        }
    }

    private void autoSizeColumns(Sheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            if (width < 3800) {
                sheet.setColumnWidth(i, 3800);
            }
        }
    }
}
