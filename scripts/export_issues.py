#!/usr/bin/env python3
"""
Script de generación y sincronización de Issues para GYM VIP (VIP CENTER FIT)
Organización: gymvip-upeu
Proyecto: GIMNASIO VIP | Product Backlog

Genera la propuesta de 34 Historias de Usuario con sus textos originales de
Product Backlog V1.0.xlsx y subtareas detalladas exclusivamente para E01 y E02.
Por defecto opera en modo --dry-run (no interactúa con la API remota de GitHub).
"""

import sys
import json
import argparse
from pathlib import Path

# Subtareas detalladas para E01 y E02 según el refinamiento canónico de PRD.md
E01_E02_SUBTASKS = {
    "HU-01": [
        "01-01: Revisar lista documental y textos legales con el gimnasio [Negocio/Legal]",
        "01-02: Diseñar formulario y variante menor bloqueada [Figma/UX]",
        "01-03: Crear modelo Client, Document, ConsentRecord con índice único compuesto [BD]",
        "01-04: Implementar endpoint POST /api/v1/clients con manejo de 409 Conflict [Backend]",
        "01-05: Construir formulario React con casillas de consentimiento separadas [Web]",
        "01-06: Pruebas automatizadas de duplicados, validación de edad y consentimientos [QA]",
        "01-07: Documentar contrato OpenAPI y ejemplo anonimizado [Documentación]"
    ],
    "HU-02": [
        "02-01: Definir campos visibles y editables por rol [Negocio]",
        "02-02: Implementar endpoint GET /api/v1/clients con búsqueda y paginación [Backend]",
        "02-03: Diseñar e implementar tabla de socios y vista detalle [Web]",
        "02-04: Implementar PATCH /api/v1/clients/{id}/contacts con evento de auditoría [Backend]",
        "02-05: Bandeja de excepción para rectificación de documento de identidad [Backend/Web P1]",
        "02-06: Pruebas de integración de filtros y auditoría de contactos [QA]"
    ],
    "HU-03": [
        "03-01: Definir identificadores y política de sesiones compartidas en recepción [Negocio]",
        "03-02: Especificar flujo de login y recuperación segura por correo [UX]",
        "03-03: Modelar entidades Account, Credentials y Session persistente [Backend]",
        "03-04: Implementar endpoints /api/v1/auth/login y /logout con hashing BCrypt [Backend]",
        "03-05: Construir pantalla de login con estados de error y carga en React [Web]",
        "03-06: Pruebas de autenticación, límite de intentos y revocación [QA]"
    ],
    "HU-04": [
        "04-01: Ratificar matriz de roles (SUPERADMIN, ADMIN, RECEPCION, CLIENTE) [Negocio]",
        "04-02: Configurar filtros Spring Security y reglas @PreAuthorize por rol [Backend]",
        "04-03: Seed seguro de cuentas de personal con contraseñas encriptadas [Backend]",
        "04-04: Implementar servicio de auditoría inmutable AuditService [Backend]",
        "04-05: Pruebas automatizadas de denegación de permisos (403 Forbidden) [QA]"
    ],
    "HU-05": [
        "05-01: Definir flujo de activación de cuenta móvil por enlace de un solo uso [Negocio]",
        "05-02: Vincular 1 a 1 Client con Account de socio [Backend]",
        "05-03: Implementar adaptador de invitación de correo vía microservicio [Backend/Comms]",
        "05-04: Vista web de estado de activación y revocación de cuenta [Web]",
        "05-05: Pruebas de vencimiento y recuperación de enlace [QA]"
    ],
    "HU-06": [
        "06-01: Definir catálogo inicial de planes con precio en PEN y duración [Negocio]",
        "06-02: Diseñar tarjeta de plan y modal de creación/edición [UX/Figma]",
        "06-03: Modelar Plan y PlanVersion para snapshot contractual inmutable [BD]",
        "06-04: Implementar POST /api/v1/plans y GET /api/v1/plans [Backend]",
        "06-05: Construir catálogo y formulario integrado en React [Web]",
        "06-06: Implementar edición e inactivación con nueva versión snapshot (P1) [Backend/Web]",
        "06-07: Pruebas de persistencia y recarga de planes tras reinicio de servidor [QA]"
    ],
    "HU-07": [
        "07-01: Definir casos fechados y reglas de inicio efectivo de membresía [Negocio]",
        "07-02: Diseñar flujo de selección de cliente, plan y resumen contractual [UX]",
        "07-03: Modelar Membership con referencia a PlanVersion snapshot [BD]",
        "07-04: Implementar servicio transaccional assignMembership con idempotencia [Backend]",
        "07-05: Construir formulario de contratación en recepción [Web]",
        "07-06: Pruebas de prevención de dobles suscripciones y concurrencia [QA]"
    ],
    "HU-08": [
        "08-01: Determinar medios de pago presenciales (efectivo, Yape, Plin, POS, transf.) [Negocio]",
        "08-02: Modelar PaymentRecord enlazado atómicamente a Membership [BD/Backend]",
        "08-03: Interfaz de registro de cobro en mostrador e historial de pagos [Web]",
        "08-04: Registro auditable de pagos y bloqueo de borrado físico [Backend]",
        "08-05: Pruebas de idempotencia de cobro ante reintentos de red [QA]"
    ],
    "HU-09": [
        "09-01: Validar tabla de casos fechados de 28/29/30/31 días y regla inclusiva [Negocio]",
        "09-02: Diseñar máquina de estados de membresía (Vigente, Próxima a vencer, Vencida) [Análisis]",
        "09-03: Implementar servicio de cálculo de vigencias con Clock inyectado en zona Lima [Backend]",
        "09-04: Visualización de badges de estado semántico en interfaz [Web]",
        "09-05: Pruebas unitarias de bordes de calendario y fin de mes [QA]"
    ],
    "HU-10": [
        "10-01: Validar escenarios de renovación anticipada y tardía [Negocio]",
        "10-02: Diseñar vista comparativa de período anterior y nuevo período [UX]",
        "10-03: Implementar transacción de renovación preservando histórico [Backend]",
        "10-04: Pantalla de confirmación y cobro presencial en web [Web]",
        "10-05: Pruebas de encadenamiento de membresías sin solapamiento [QA]"
    ],
    "HU-11": [
        "11-01: Definir umbrales de anticipación para avisos de vencimiento [Negocio]",
        "11-02: Implementar consulta paginada de membresías por estado [Backend]",
        "11-03: Construir tabla filtrable de vencimientos en web [Web]",
        "11-04: Pruebas de rendimiento de consulta de vencimientos [QA]"
    ],
    "HU-12": [
        "12-01: Diseñar pantalla de verificación rápida de socio en recepción [UX]",
        "12-02: Endpoint optimizado de consulta de vigencia por DNI [Backend]",
        "12-03: Componente de verificación rápida con indicador visual y sonoro [Web]",
        "12-04: Pruebas de detección de socios con membresía vencida [QA]"
    ]
}

def generate_issues():
    # 34 HU exactas extraídas de Product Backlog GYM VIP V1.0.xlsx
    raw_stories = [
        ("HU-01", "E01 · Gestión de usuarios y clientes", "Registrar clientes", "Como administrador, quiero registrar los datos de los clientes para mantener un control organizado de las personas inscritas en el gimnasio.", "Must Have", "WEB"),
        ("HU-02", "E01 · Gestión de usuarios y clientes", "Consultar y actualizar clientes", "Como personal autorizado, quiero consultar y actualizar los datos de los clientes para mantener su información vigente.", "Must Have", "WEB"),
        ("HU-03", "E01 · Gestión de usuarios y clientes", "Inicio de sesión", "Como usuario autorizado, quiero iniciar sesión mediante mis credenciales para acceder de forma segura al sistema según mi rol.", "Must Have", "AMBOS"),
        ("HU-04", "E01 · Gestión de usuarios y clientes", "Roles y permisos", "Como administrador, quiero gestionar los roles y permisos del personal para controlar el acceso a las funcionalidades de la plataforma.", "Must Have", "WEB"),
        ("HU-05", "E01 · Gestión de usuarios y clientes", "Habilitar acceso móvil", "Como administrador, quiero habilitar el acceso móvil de un cliente registrado para que pueda utilizar los servicios digitales de GYM VIP.", "Must Have", "AMBOS"),
        ("HU-06", "E02 · Membresías, pagos y renovaciones", "Configurar planes", "Como administrador, quiero registrar y configurar los planes de membresía con sus precios, duración, condiciones y beneficios para informar correctamente los servicios que ofrece el gimnasio.", "Must Have", "WEB"),
        ("HU-07", "E02 · Membresías, pagos y renovaciones", "Asignar membresías", "Como personal de recepción, quiero asignar una membresía a un cliente para registrar el servicio que ha contratado.", "Must Have", "WEB"),
        ("HU-08", "E02 · Membresías, pagos y renovaciones", "Registrar pagos", "Como personal de recepción, quiero registrar los pagos de las membresías para mantener actualizado el historial de las operaciones realizadas.", "Must Have", "WEB"),
        ("HU-09", "E02 · Membresías, pagos y renovaciones", "Controlar vigencias", "Como administrador, quiero consultar las fechas de inicio, vencimiento y estado de las membresías para identificar cuáles se encuentran vigentes, próximas a vencer o vencidas.", "Must Have", "WEB / API"),
        ("HU-10", "E02 · Membresías, pagos y renovaciones", "Renovar membresías", "Como personal de recepción, quiero renovar la membresía de un cliente para registrar correctamente su nuevo periodo de vigencia.", "Must Have", "WEB"),
        ("HU-11", "E02 · Membresías, pagos y renovaciones", "Listado de vencimientos", "Como administrador, quiero visualizar un listado de membresías próximas a vencer y vencidas para realizar un seguimiento oportuno de los clientes.", "Must Have", "WEB"),
        ("HU-12", "E02 · Membresías, pagos y renovaciones", "Verificar membresía", "Como personal autorizado, quiero verificar el estado de la membresía de un cliente para identificar posibles situaciones de asistencia con una membresía vencida.", "Must Have", "WEB"),
        ("HU-13", "E03 · Atención, comunicación y notificaciones", "Configurar recordatorios", "Como administrador, quiero configurar recordatorios automáticos de vencimiento de membresías para reducir la dependencia del seguimiento manual.", "Must Have", "WEB / MICRO"),
        ("HU-14", "E03 · Atención, comunicación y notificaciones", "Recibir avisos", "Como cliente, quiero recibir avisos relacionados con el estado de mi membresía para mantenerme informado sobre su próxima renovación.", "Must Have", "APP / MICRO"),
        ("HU-15", "E03 · Atención, comunicación y notificaciones", "Gestionar respuestas frecuentes", "Como administrador, quiero gestionar mensajes de respuesta a consultas frecuentes para agilizar la atención de los clientes.", "Should Have", "WEB / MICRO"),
        ("HU-16", "E03 · Atención, comunicación y notificaciones", "Comunicaciones WhatsApp", "Como administrador, quiero utilizar la integración de comunicaciones con WhatsApp para automatizar los mensajes contemplados por el gimnasio.", "Must Have", "MICRO"),
        ("HU-17", "E03 · Atención, comunicación y notificaciones", "Estado de comunicaciones", "Como personal autorizado, quiero consultar el estado de los envíos de comunicaciones para identificar mensajes enviados correctamente o intentos fallidos.", "Should Have", "WEB / MICRO"),
        ("HU-18", "E04 · Servicios y seguimiento del entrenamiento", "Registrar rutinas", "Como entrenador, quiero registrar rutinas de entrenamiento para organizar las actividades asignadas a los clientes.", "Must Have", "WEB"),
        ("HU-19", "E04 · Servicios y seguimiento del entrenamiento", "Asignar y actualizar rutinas", "Como entrenador, quiero asignar y actualizar rutinas para mantener vigente la planificación de entrenamiento de cada cliente.", "Must Have", "WEB"),
        ("HU-20", "E04 · Servicios y seguimiento del entrenamiento", "Consultar seguimiento", "Como entrenador, quiero consultar la información relacionada con las rutinas y los servicios asignados para realizar el seguimiento correspondiente.", "Should Have", "WEB"),
        ("HU-21", "E04 · Servicios y seguimiento del entrenamiento", "Registrar servicios", "Como administrador, quiero registrar la información de los servicios de entrenamiento disponibles para mantener organizado el catálogo de servicios del gimnasio.", "Should Have", "WEB"),
        ("HU-22", "E05 · Suplementos deportivos y promociones", "Registrar suplementos", "Como administrador, quiero registrar los suplementos deportivos con sus características, imágenes y precios para disponer de un catálogo digital de productos.", "Must Have", "WEB"),
        ("HU-23", "E05 · Suplementos deportivos y promociones", "Actualizar catálogo", "Como administrador, quiero actualizar la información y disponibilidad de los suplementos para mantener vigente el catálogo ofrecido a los clientes.", "Must Have", "WEB"),
        ("HU-24", "E05 · Suplementos deportivos y promociones", "Registrar promociones", "Como administrador, quiero registrar promociones de suplementos deportivos para comunicar las ofertas disponibles.", "Must Have", "WEB"),
        ("HU-25", "E05 · Suplementos deportivos y promociones", "Consultar suplementos", "Como cliente, quiero consultar información sobre los suplementos disponibles para conocer sus características antes de contactar con el gimnasio.", "Must Have", "APP"),
        ("HU-26", "E05 · Suplementos deportivos y promociones", "Consultar promociones", "Como cliente, quiero consultar las promociones vigentes para conocer las ofertas comerciales del gimnasio.", "Should Have", "APP"),
        ("HU-27", "E05 · Suplementos deportivos y promociones", "Consultar preguntas frecuentes", "Como cliente, quiero acceder a información y respuestas frecuentes sobre suplementos para resolver consultas generales sin depender siempre de recepción.", "Should Have", "APP / MICRO"),
        ("HU-28", "E06 · Aplicación móvil del cliente", "Inicio de sesión móvil", "Como cliente registrado, quiero iniciar sesión desde la aplicación móvil para acceder de manera segura a mi información personal.", "Must Have", "APP"),
        ("HU-29", "E06 · Aplicación móvil del cliente", "Consultar membresía móvil", "Como cliente, quiero consultar mi membresía, fecha de vencimiento y condiciones del plan contratado para conocer el estado de mi servicio.", "Must Have", "APP"),
        ("HU-30", "E06 · Aplicación móvil del cliente", "Consultar pagos móvil", "Como cliente, quiero consultar el historial de mis pagos registrados para realizar un seguimiento de mis mensualidades.", "Must Have", "APP"),
        ("HU-31", "E06 · Aplicación móvil del cliente", "Visualizar rutinas móvil", "Como cliente, quiero visualizar las rutinas que me han sido asignadas para consultar mi planificación de entrenamiento.", "Must Have", "APP"),
        ("HU-32", "E06 · Aplicación móvil del cliente", "Consultar servicios móvil", "Como cliente, quiero consultar los servicios ofrecidos por el gimnasio para conocer las alternativas disponibles.", "Should Have", "APP"),
        ("HU-33", "E06 · Aplicación móvil del cliente", "Consultar notificaciones móvil", "Como cliente, quiero recibir y consultar notificaciones relacionadas con mis servicios para mantenerme informado sobre avisos importantes.", "Must Have", "APP / MICRO"),
        ("HU-34", "E06 · Aplicación móvil del cliente", "Catálogo y promociones móvil", "Como cliente, quiero consultar el catálogo y las promociones de suplementos desde la aplicación móvil para conocer los productos ofrecidos por el gimnasio.", "Must Have", "APP")
    ]

    issues = []
    for hu_id, epic, name, story, priority, platform in raw_stories:
        subtasks = E01_E02_SUBTASKS.get(hu_id, [])
        issue_body = f"""### {hu_id}: {name}

**Épica:** {epic}  
**Prioridad MoSCoW:** {priority}  
**Plataforma:** {platform}  

#### Historia de Usuario
> {story}

#### Definición de Terminado y Tareas Técnicas
"""
        if subtasks:
            issue_body += "\n".join([f"- [ ] {st}" for st in subtasks])
        else:
            issue_body += "- [ ] Tareas y refinamiento técnico por acordar en planning de épica."

        issues.append({
            "id": hu_id,
            "title": f"[{hu_id}] {name}",
            "epic": epic,
            "priority": priority,
            "platform": platform,
            "subtasks_count": len(subtasks),
            "body": issue_body
        })

    return issues

def main():
    parser = argparse.ArgumentParser(description="Exportador de Issues para GYM VIP")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Modo simulación sin invocar GitHub API")
    parser.add_argument("--out", type=str, default="", help="Ruta de guardado del manifiesto JSON")
    args = parser.parse_args()

    print("=================================================================")
    print(" GYM VIP — Generador de Manifiesto de Issues para GitHub Project")
    print(" Organización objetivo: gymvip-upeu")
    print(" Proyecto: GIMNASIO VIP | Product Backlog")
    print(" Modo de ejecución: " + ("DRY-RUN (Simulación Segura)" if args.dry_run else "PUBLICACIÓN REMOTA"))
    print("=================================================================\n")

    issues = generate_issues()
    print(f"Total de Historias de Usuario procesadas: {len(issues)}")
    
    e01_e02_count = sum(1 for i in issues if i["subtasks_count"] > 0)
    print(f"Historias con subtareas detalladas (E01 y E02): {e01_e02_count}")
    print(f"Historias de épicas E03 a E06 listas para refinamiento: {len(issues) - e01_e02_count}\n")

    for issue in issues[:6]:
        print(f"[OK] {issue['title']} | Epica: {issue['epic'][:25]}... | Subtareas: {issue['subtasks_count']}")
    print(f"... y {len(issues) - 6} historias más.\n")

    # Guardar manifiesto
    out_path = args.out if args.out else Path(__file__).parent.parent / "docs" / "producto" / "ISSUES_MANIFEST.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(issues, f, indent=2, ensure_ascii=False)
    print(f"Manifiesto exportado exitosamente a: {out_path}")
    print("\nAVISO DE SEGURIDAD: Ningún Issue remoto ha sido creado en GitHub de conformidad con la directiva del proyecto.")

if __name__ == "__main__":
    main()
