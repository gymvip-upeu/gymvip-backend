package com.gymvip.enums;

public enum Role {
    // Nivel Plataforma (Proveedor SaaS GYM VIP)
    SUPER_ADMIN,
    SOPORTE_PLATAFORMA,

    // Nivel Empresarial (Organización / Cadena Cliente)
    ADMIN_EMPRESA,
    GERENTE_GENERAL,
    AUDITOR_EMPRESA,
    CONTADOR,
    RECURSOS_HUMANOS,

    // Nivel Sede (Operación Local)
    GERENTE_SEDE,
    RECEPCION,
    CAJERO,
    INVENTARIO,
    COMPRAS,
    ENTRENADOR_JEFE,
    ENTRENADOR,
    MARKETING,
    ATENCION_CLIENTE,
    MANTENIMIENTO,

    // Compatibilidad histórica (en migración controlada hacia SUPER_ADMIN / ADMIN_EMPRESA / GERENTE_GENERAL / AUDITOR_EMPRESA)
    ADMIN,       // Soporte Técnico / Administrador heredado de plataforma y empresa
    GERENTE,     // Dirección y Finanzas heredado
    AUDITOR,     // Control Interno y Supervisión de Operaciones heredado

    // Nivel Socio / Atleta
    CLIENTE
}
