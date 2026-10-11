# Modelo C4 — Arquitectura de GYM VIP

**Niveles incluidos:** Nivel 1 (Contexto de Sistema), Nivel 2 (Contenedores) y Nivel 3 (Componentes del Backend Núcleo).

---

## Nivel 1: Diagrama de Contexto de Sistema (C1)

Describe a los usuarios clave y cómo interactúan con el ecosistema de software de VIP CENTER FIT frente a sistemas externos.

```mermaid
C4Context
  title Diagrama de Contexto del Sistema GYM VIP

  Person(recepcion, "Personal de Recepción", "Atiende socios en el mostrador, registra altas, cobra planes presenciales y consulta membresías.")
  Person(admin, "Administrador / Superadmin", "Configura planes, supervisa finanzas, gestiona accesos y autoriza excepciones.")
  Person(socio, "Socio del Gimnasio", "Consulta el estado de su membresía y el historial de pagos desde su dispositivo móvil.")

  System(gymvip, "Plataforma GYM VIP", "Sistema centralizado de administración de membresías, clientes y cobros presenciales.")

  System_Ext(whatsapp, "Servicio de WhatsApp", "Entrega notificaciones de recordatorios y confirmaciones vía Meta Cloud API o adaptador simulado.")
  System_Ext(email, "Servicio de Correo", "Entrega enlaces de activación y avisos transaccionales vía SMTP o adaptador simulado.")

  Rel(recepcion, gymvip, "Registra socios, asigna membresías y cobra presencialmente", "HTTPS / Web")
  Rel(admin, gymvip, "Configura catálogo de planes y audita operaciones", "HTTPS / Web")
  Rel(socio, gymvip, "Consulta vigencia de membresía e historial de pagos", "HTTPS / App Móvil")

  Rel(gymvip, whatsapp, "Envía recordatorios de vencimiento", "REST API / JSON")
  Rel(gymvip, email, "Envía enlaces de activación de cuenta", "SMTP / REST API")
```

---

## Nivel 2: Diagrama de Contenedores (C2)

Muestra los subsistemas ejecutables independientes que componen GYM VIP.

```mermaid
C4Container
  title Diagrama de Contenedores de GYM VIP

  Person(staff, "Personal de Recepción / Admin", "Personal interno del gimnasio")
  Person(socio, "Socio", "Cliente con cuenta habilitada")

  Container(web_app, "Aplicación Web Administrativa", "React 19, Vite, Vanilla CSS", "Interfaz responsiva para gestión de clientes, catálogo de planes y cobros presenciales.")
  Container(mobile_app, "Aplicación Móvil Socio", "Kotlin Multiplatform (Android/iOS)", "Interfaz nativa y ligera para consulta de membresía, pagos y notificaciones.")
  Container(backend_api, "Backend Núcleo de Negocio", "Java 21/24, Spring Boot 3.4", "Provee la lógica de negocio, validaciones, control de roles, transacciones y auditoría.")
  Container(comms_service, "Microservicio de Comunicaciones", "Java / Spring Boot", "Gestiona la cola y despacho de mensajes hacia WhatsApp y correo electrónico con stubs de prueba.")
  ContainerDb(database, "Base de Datos Principal", "PostgreSQL / Storage Durable", "Almacena cuentas, clientes, contratos, pagos, eventos de auditoría y catálogo de planes.")

  Rel(staff, web_app, "Usa interfaz en navegador", "HTTPS")
  Rel(socio, mobile_app, "Usa aplicación en teléfono", "HTTPS")

  Rel(web_app, backend_api, "Llama endpoints REST", "JSON / HTTPS")
  Rel(mobile_app, backend_api, "Consulta endpoints REST de socio", "JSON / HTTPS")

  Rel(backend_api, database, "Lee y escribe datos transaccionales", "JDBC / JPA")
  Rel(backend_api, comms_service, "Solicita despacho de notificaciones", "REST / HTTP")
```

---

## Nivel 3: Diagrama de Componentes del Backend (C3)

Desglose de los componentes lógicos que conforman el contenedor `Backend Núcleo de Negocio`.

```mermaid
C4Component
  title Diagrama de Componentes del Backend Núcleo

  Container_Boundary(api_boundary, "Spring Boot Core API")
    Component(auth_ctrl, "AuthController", "Spring REST Controller", "Expone login, logout y estado de sesión.")
    Component(client_ctrl, "ClientController", "Spring REST Controller", "Expone registro y búsqueda de socios adultos.")
    Component(plan_ctrl, "PlanController", "Spring REST Controller", "Expone alta, edición y consulta de planes.")
    Component(membership_ctrl, "MembershipController", "Spring REST Controller", "Expone asignación transaccional y consulta de vigencia.")

    Component(security_filter, "SecurityFilter & RBAC", "Spring Security", "Intercepta solicitudes, valida tokens/cookies y roles.")
    Component(audit_service, "AuditService", "Spring Service", "Registra eventos inmutables de auditoría.")

    Component(client_service, "ClientService", "Spring Service", "Gestiona reglas de clientes, unicidad y consentimientos.")
    Component(plan_service, "PlanService", "Spring Service", "Gestiona catálogo y versionado de planes.")
    Component(membership_service, "MembershipService", "Spring Service", "Orquesta transacción atómica de membresía y pago.")

    Component(db_repos, "JPA Repositories", "Spring Data JPA", "Acceso a tablas de base de datos.")
  end

  Rel(auth_ctrl, security_filter, "Valida credenciales")
  Rel(client_ctrl, client_service, "Delega lógica")
  Rel(plan_ctrl, plan_service, "Delega lógica")
  Rel(membership_ctrl, membership_service, "Delega lógica")

  Rel(client_service, audit_service, "Emite evento")
  Rel(membership_service, audit_service, "Emite evento")

  Rel(client_service, db_repos, "Persiste socios")
  Rel(plan_service, db_repos, "Persiste planes")
  Rel(membership_service, db_repos, "Persiste contratos y pagos")
```
