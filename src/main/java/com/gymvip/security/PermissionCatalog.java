package com.gymvip.security;

import java.util.*;

/**
 * Catálogo centralizado de roles empresariales y permisos granulares (RBAC).
 * Fuente única de verdad para Backend (@PreAuthorize / SecurityFilterChain) y endpoints /api/v1/rbac.
 */
public final class PermissionCatalog {

    public record RoleDefinition(
            String code,
            String displayName,
            String level, // PLATFORM | ORGANIZATION | BRANCH | CLIENT
            String description,
            boolean privileged,
            boolean implementedInProduct,
            Set<String> defaultPermissions
    ) {}

    public record PermissionDefinition(
            String code,
            String module,
            String action,
            String description
    ) {}

    private static final List<PermissionDefinition> PERMISSIONS = List.of(
            // Plataforma y Seguridad
            new PermissionDefinition("platform.manage", "Plataforma", "administrar", "Administrar empresas contratantes, versiones SaaS y políticas globales"),
            new PermissionDefinition("integrations.read", "Integraciones", "lectura", "Consultar estado de canales WhatsApp, Email, Push y pasarelas"),
            new PermissionDefinition("integrations.test", "Integraciones", "probar", "Ejecutar pruebas de conectividad y diagnóstico de integraciones"),
            new PermissionDefinition("security.roles.manage", "Seguridad", "roles", "Asignar roles operativos y supervisar cuentas de personal"),
            new PermissionDefinition("security.permissions.manage", "Seguridad", "permisos", "Administrar catálogo y asignaciones de permisos granulares"),
            new PermissionDefinition("branches.read", "Organización", "lectura", "Consultar empresas y sedes registradas"),
            new PermissionDefinition("branches.manage", "Organización", "administrar", "Crear y administrar sedes operativas e instalaciones"),

            // Miembros y Membresías
            new PermissionDefinition("members.read", "Miembros", "lectura", "Consultar directorio y fichas de socios"),
            new PermissionDefinition("members.create", "Miembros", "creacion", "Registrar nuevos socios individuales y apoderados"),
            new PermissionDefinition("members.update", "Miembros", "edicion", "Editar ficha personal y datos de contacto de socios"),
            new PermissionDefinition("members.import", "Miembros", "importacion", "Ejecutar importación masiva .xlsx y migración ETL"),
            new PermissionDefinition("memberships.read", "Membresías", "lectura", "Consultar vigencia, planes y estado de membresías"),
            new PermissionDefinition("memberships.create", "Membresías", "creacion", "Asignar membresías nuevas y emitir enlaces digitales"),
            new PermissionDefinition("memberships.renew", "Membresías", "edicion", "Renovar, reemplazar o cancelar periodos de membresía"),

            // Pagos y Asistencia
            new PermissionDefinition("payments.read", "Finanzas", "lectura", "Visualizar cobros, recaudación y comprobantes"),
            new PermissionDefinition("payments.create", "Finanzas", "creacion", "Registrar cobros presenciales y generar órdenes CulqiLink"),
            new PermissionDefinition("payments.refund", "Finanzas", "aprobacion", "Anular cobros o gestionar devoluciones autorizadas"),
            new PermissionDefinition("attendance.read", "Operaciones", "lectura", "Consultar historial y agenda de asistencias"),
            new PermissionDefinition("attendance.manage", "Operaciones", "edicion", "Validar accesos QR en recepción y operar terminal"),

            // Catálogo, Planes e Inventario
            new PermissionDefinition("plans.read", "Catálogo", "lectura", "Consultar planes y tarifario vigente"),
            new PermissionDefinition("plans.manage", "Catálogo", "edicion", "Crear y desactivar versiones de planes tarifarios"),
            new PermissionDefinition("inventory.read", "Inventario", "lectura", "Consultar suplementos, precios y stock"),
            new PermissionDefinition("inventory.adjust", "Inventario", "edicion", "Ajustar kardex, stock, promociones y catálogo"),
            new PermissionDefinition("inventory.transfer", "Inventario", "transferencia", "Transferir stock entre sedes"),
            new PermissionDefinition("orders.dispatch", "Pedidos", "edicion", "Preparar y entregar pedidos en la sede"),
            new PermissionDefinition("communications.drafts", "Comunicaciones", "edicion", "Preparar anuncios de la sede para revisión"),
            new PermissionDefinition("incidents.close", "Operación", "edicion", "Cerrar incidencias con motivo registrado"),
            new PermissionDefinition("facilities.update_status", "Operación", "edicion", "Cambiar la disponibilidad de áreas de su sede"),

            // Personal
            new PermissionDefinition("employees.read", "Personal", "lectura", "Consultar nómina de colaboradores"),
            new PermissionDefinition("employees.create", "Personal", "creacion", "Registrar nuevas cuentas de colaboradores"),
            new PermissionDefinition("employees.assign_role", "Personal", "roles", "Modificar rol o sede asignada de colaboradores"),
            new PermissionDefinition("employees.inactivate", "Personal", "inactivacion", "Revocar acceso e inactivar cuentas no críticas"),

            // Entrenamiento y Reservas
            new PermissionDefinition("supplements.stock.read", "Suplementos", "lectura", "Consultar las existencias y el kardex de la sede"),
            new PermissionDefinition("supplements.stock.receive", "Suplementos", "entrada", "Registrar entradas de stock vendible"),
            new PermissionDefinition("supplements.stock.adjust", "Suplementos", "ajuste", "Registrar salidas y ajustes de stock vendible"),
            new PermissionDefinition("training.exercises.edit", "Entrenamiento", "edicion", "Crear y editar borradores de ejercicios de la sede"),
            new PermissionDefinition("training.exercises.publish", "Entrenamiento", "publicacion", "Publicar fichas de ejercicios revisadas"),
            new PermissionDefinition("training.classes.roster", "Reservas", "lectura", "Consultar la lista de reservas de clases autorizadas"),
            new PermissionDefinition("training.classes.notifications", "Reservas", "avisos", "Recibir avisos de reservas de clases asignadas"),
            new PermissionDefinition("training.routines.read", "Entrenamiento", "lectura", "Consultar catálogo de ejercicios y rutinas"),
            new PermissionDefinition("training.routines.create", "Entrenamiento", "creacion", "Crear rutinas estructuradas"),
            new PermissionDefinition("training.routines.assign", "Entrenamiento", "asignacion", "Asignar rutinas a socios autorizados"),
            new PermissionDefinition("training.progress.read", "Entrenamiento", "progreso", "Consultar progreso y métricas de entrenamiento"),
            new PermissionDefinition("training.sessions.log", "Entrenamiento", "registro", "Registrar sesiones, series, repeticiones y cargas"),
            new PermissionDefinition("training.classes.reserve", "Reservas", "reserva", "Reservar o cancelar cupos en clases grupales"),
            new PermissionDefinition("training.classes.manage", "Reservas", "administrar", "Publicar clases grupales, horarios y cupos por sede"),

            // Reportes, Incidencias y Comunicaciones
            new PermissionDefinition("reports.read", "Analítica", "lectura", "Visualizar métricas, KPIs financieros y reportes"),
            new PermissionDefinition("reports.export", "Analítica", "exportacion", "Exportar reportes PDF y Excel"),
            new PermissionDefinition("incidents.read", "Auditoría", "lectura", "Consultar libro de incidencias y auditoría"),
            new PermissionDefinition("incidents.manage", "Auditoría", "edicion", "Registrar y cerrar incidencias operativas"),
            new PermissionDefinition("communications.read", "Comunicaciones", "lectura", "Consultar FAQs e historial de envíos"),
            new PermissionDefinition("communications.configure", "Comunicaciones", "edicion", "Configurar recordatorios, plantillas y comunicados"),
            new PermissionDefinition("preferences.manage", "Cuenta", "preferencias", "Administrar preferencia de guías, apariencia y sede preferida")
    );

    private static final Set<String> STAFF_CORE_READ = Set.of(
            "members.read", "memberships.read", "payments.read", "attendance.read",
            "plans.read", "inventory.read", "communications.read", "branches.read",
            "training.routines.read", "training.classes.roster", "supplements.stock.read", "preferences.manage"
    );

    private static final Set<String> RECEPTION_PERMS = union(STAFF_CORE_READ, Set.of(
            "members.create", "members.update", "memberships.create", "memberships.renew",
            "payments.create", "attendance.manage", "orders.dispatch", "facilities.update_status",
            "incidents.read", "incidents.manage", "communications.drafts"
    ));

    private static final Set<String> COACH_PERMS = Set.of(
            "branches.read", "members.read", "training.routines.read", "training.routines.create",
            "training.routines.assign", "training.progress.read", "training.sessions.log",
            "training.classes.manage", "training.classes.roster", "training.classes.notifications",
            "training.exercises.edit", "training.exercises.publish", "communications.read", "preferences.manage"
    );

    private static final Set<String> AUDITOR_PERMS = Set.of(
            "branches.read", "reports.read", "reports.export", "incidents.read", "incidents.close",
            "payments.read", "attendance.read", "employees.read", "preferences.manage"
    );

    private static final Set<String> MANAGER_BRANCH_PERMS = union(RECEPTION_PERMS, Set.of(
            "inventory.adjust", "inventory.transfer", "supplements.stock.receive", "supplements.stock.adjust",
            "training.exercises.edit", "training.classes.notifications", "training.routines.read", "training.routines.create",
            "training.routines.assign", "training.progress.read", "training.classes.manage",
            "reports.read", "reports.export", "incidents.read", "incidents.manage",
            "employees.read", "communications.configure"
    ));

    private static final Set<String> CORP_ADMIN_PERMS = union(MANAGER_BRANCH_PERMS, Set.of(
            "members.import", "plans.manage", "payments.refund", "employees.create",
            "employees.assign_role", "employees.inactivate", "branches.manage",
            "integrations.read", "integrations.test", "security.roles.manage", "training.exercises.publish"
    ));

    private static final Set<String> SUPER_ADMIN_PERMS = union(CORP_ADMIN_PERMS, Set.of(
            "platform.manage", "security.permissions.manage"
    ));

    private static final Set<String> CLIENT_PERMS = Set.of(
            "branches.read", "memberships.read", "plans.read", "inventory.read",
            "attendance.read", "communications.read", "training.routines.read",
            "training.routines.create", "training.progress.read", "training.sessions.log",
            "training.classes.reserve", "preferences.manage"
    );

    private static final Set<String> RETIRED_ROLES = Set.of("CAJERO", "INVENTARIO", "COMPRAS", "ENTRENADOR_JEFE",
            "MARKETING", "ATENCION_CLIENTE", "MANTENIMIENTO");

    private static final Map<String, RoleDefinition> ROLES = new LinkedHashMap<>();

    static {
        register("SUPER_ADMIN", "Super Administrador de Plataforma", "PLATFORM",
                "Responsable global del producto SaaS GYM VIP, empresas contratantes, políticas de seguridad e infraestructura.",
                true, true, SUPER_ADMIN_PERMS);
        register("SOPORTE_PLATAFORMA", "Soporte de Plataforma", "PLATFORM",
                "Diagnóstico técnico controlado de integraciones, incidencias y cuentas con auditoría estricta.",
                true, true, Set.of("branches.read", "integrations.read", "integrations.test", "incidents.read", "reports.read", "preferences.manage"));

        register("ADMIN_EMPRESA", "Administrador de Empresa", "ORGANIZATION",
                "Administrador general de la empresa o cadena de gimnasios contratante.",
                true, true, CORP_ADMIN_PERMS);
        register("GERENTE_GENERAL", "Gerente General", "ORGANIZATION",
                "Dirección ejecutiva y financiera multi-sede.",
                false, true, union(MANAGER_BRANCH_PERMS, Set.of("plans.manage", "employees.create", "employees.assign_role", "employees.inactivate", "branches.manage", "integrations.read", "training.exercises.publish")));
        register("AUDITOR_EMPRESA", "Auditor Empresarial", "ORGANIZATION",
                "Consulta de operaciones y finanzas y cierre motivado de incidencias.",
                false, true, AUDITOR_PERMS);
        register("CONTADOR", "Contador", "ORGANIZATION",
                "Control tributario, recaudación y exportación de reportes financieros.",
                false, false, Set.of("branches.read", "payments.read", "reports.read", "reports.export", "preferences.manage"));
        register("RECURSOS_HUMANOS", "Recursos Humanos", "ORGANIZATION",
                "Gestión de nómina de personal, altas y asignaciones por sede.",
                false, false, Set.of("branches.read", "employees.read", "employees.create", "employees.inactivate", "preferences.manage"));

        register("GERENTE_SEDE", "Responsable de sede", "BRANCH",
                "Gestión integral de una sede asignada: operaciones, personal local, clases e inventario.",
                false, true, MANAGER_BRANCH_PERMS);
        register("RECEPCION", "Colaborador", "BRANCH",
                "Atención y caja de la sede, asistencia, entrega de pedidos, incidencias, estado de áreas y borradores de avisos.",
                false, true, RECEPTION_PERMS);
        register("CAJERO", "Cajero", "BRANCH",
                "Cobros presenciales, emisión de enlaces de pago y cierre de caja.",
                false, false, Set.of("branches.read", "members.read", "memberships.read", "memberships.create", "memberships.renew", "payments.read", "payments.create", "orders.dispatch", "preferences.manage"));
        register("INVENTARIO", "Encargado de Inventario", "BRANCH",
                "Control de catálogo, ajuste de kardex y transferencias de suplementos.",
                false, true, Set.of("branches.read", "inventory.read", "inventory.adjust", "inventory.transfer",
                        "supplements.stock.read", "supplements.stock.receive", "preferences.manage"));
        register("COMPRAS", "Compras y Abastecimiento", "BRANCH",
                "Reposición de inventario y seguimiento de proveedores (planificado).",
                false, false, Set.of("branches.read", "inventory.read", "preferences.manage"));
        register("ENTRENADOR_JEFE", "Entrenador Jefe", "BRANCH",
                "Supervisión metodológica de entrenadores, clases y rutinas de la sede.",
                false, true, union(COACH_PERMS, Set.of("reports.read", "training.exercises.publish")));
        register("ENTRENADOR", "Entrenador", "BRANCH",
                "Rutinas, clases propias, seguimiento de socios asignados y publicación de ejercicios de su sede.",
                false, true, COACH_PERMS);
        register("MARKETING", "Marketing y Promociones", "BRANCH",
                "Gestión de campañas, promociones y comunicados institucionales.",
                false, false, Set.of("branches.read", "communications.read", "communications.configure", "preferences.manage"));
        register("ATENCION_CLIENTE", "Atención al Cliente", "BRANCH",
                "Soporte a socios, FAQs y seguimiento de incidencias básicas.",
                false, false, Set.of("branches.read", "members.read", "memberships.read", "communications.read", "incidents.read", "preferences.manage"));
        register("MANTENIMIENTO", "Mantenimiento e Instalaciones", "BRANCH",
                "Gestión de áreas e incidencias físicas del gimnasio.",
                false, false, Set.of("branches.read", "incidents.read", "incidents.manage", "preferences.manage"));

        // Roles heredados conservados para compatibilidad retrospectiva (sin privilegios de plataforma)
        register("ADMIN", "Administrador Técnico (Legacy)", "ORGANIZATION",
                "Rol administrativo heredado acotado a su organización; no otorga gobierno global SaaS.",
                true, true, CORP_ADMIN_PERMS);
        register("GERENTE", "Gerente (Legacy)", "ORGANIZATION",
                "Rol gerencial heredado equivalente a GERENTE_GENERAL.",
                false, true, union(MANAGER_BRANCH_PERMS, Set.of("plans.manage", "employees.create", "employees.assign_role", "employees.inactivate", "branches.manage", "integrations.read", "training.exercises.publish")));
        register("AUDITOR", "Auditor (Legacy)", "ORGANIZATION",
                "Rol auditor heredado equivalente a AUDITOR_EMPRESA.",
                false, true, AUDITOR_PERMS);

        register("CLIENTE", "Socio / Atleta", "CLIENT",
                "Autoservicio móvil: membresía, QR de acceso, entrenamiento offline, reservas y tienda.",
                false, true, CLIENT_PERMS);
    }

    private PermissionCatalog() {
    }

    private static void register(String code, String displayName, String level, String description,
                                 boolean privileged, boolean implementedInProduct, Set<String> perms) {
        ROLES.put(code, new RoleDefinition(code, displayName, level, description, privileged, implementedInProduct, Set.copyOf(perms)));
    }

    private static Set<String> union(Set<String> base, Set<String> extra) {
        Set<String> merged = new LinkedHashSet<>(base);
        merged.addAll(extra);
        return Set.copyOf(merged);
    }

    public static Set<String> resolvePermissions(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String role : roles) {
            String normalized = normalizeRole(role);
            RoleDefinition def = ROLES.get(normalized);
            if (def != null) {
                resolved.addAll(def.defaultPermissions());
            }
        }
        return Set.copyOf(resolved);
    }

    public static boolean hasPermission(Collection<String> roles, String permission) {
        return resolvePermissions(roles).contains(permission);
    }

    public static boolean isStaffRole(String role) {
        String normalized = normalizeRole(role);
        RoleDefinition def = ROLES.get(normalized);
        return def != null && !"CLIENT".equals(def.level());
    }

    public static boolean isAssignableStaffRole(String role) {
        return isStaffRole(role) && !isRetiredRole(role);
    }

    public static boolean isRetiredRole(String role) {
        return RETIRED_ROLES.contains(normalizeRole(role));
    }

    public static boolean isSuperAdmin(Collection<String> roles) {
        return containsNormalized(roles, "SUPER_ADMIN");
    }

    public static boolean isPlatformOrLegacyAdmin(Collection<String> roles) {
        return containsNormalized(roles, "SUPER_ADMIN") || containsNormalized(roles, "ADMIN");
    }

    public static boolean containsNormalized(Collection<String> roles, String expected) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (normalizeRole(role).equals(expected)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        String clean = role.trim().toUpperCase(Locale.ROOT);
        return clean.startsWith("ROLE_") ? clean.substring(5) : clean;
    }

    public static String canonicalRole(String role) {
        return switch (normalizeRole(role)) {
            case "ADMIN" -> "ADMIN_EMPRESA";
            case "GERENTE" -> "GERENTE_GENERAL";
            case "AUDITOR" -> "AUDITOR_EMPRESA";
            default -> normalizeRole(role);
        };
    }

    public static Set<String> compatibleRoles(Collection<String> roles) {
        Set<String> compatible = new LinkedHashSet<>();
        for (String role : roles) {
            String canonical = canonicalRole(role);
            compatible.add(canonical);
            switch (canonical) {
                case "ADMIN_EMPRESA" -> compatible.add("ADMIN");
                case "GERENTE_GENERAL" -> compatible.add("GERENTE");
                case "AUDITOR_EMPRESA" -> compatible.add("AUDITOR");
            }
        }
        return Set.copyOf(compatible);
    }

    public static RoleDefinition resolveRoleDefinition(String role) {
        return ROLES.get(normalizeRole(role));
    }

    public static List<RoleDefinition> allRoleDefinitions() {
        return ROLES.values().stream()
                .filter(role -> role.code().equals(canonicalRole(role.code())) && !isRetiredRole(role.code()))
                .toList();
    }

    public static List<PermissionDefinition> allPermissionDefinitions() {
        return PERMISSIONS;
    }
}
