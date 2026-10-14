# Modelos y Dinámica del Negocio — Base Funcional GYM VIP
**Responsable:** André Acosta (Sprint 1)  
**Dominio:** Clientes (HU-01), Personal y Roles (HU-04), Planes y Tarifas (HU-06)  

---

## 1. Matriz de Trazabilidad Extremo a Extremo

| Historia de Usuario | Caso de Uso (Application) | Endpoint HTTP (API) | Componente UI (Frontend) | Tablas PostgreSQL | Pruebas Automatizadas |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **HU-01: Registro de Clientes** | `RegisterClientUseCase`<br/>(`RegisterClientService`) | `POST /api/v1/clients` | `ClientRegisterModal.jsx`<br/>`ClientsPage.jsx` | `clients`<br/>`documents`<br/>`audit_events` | `ClientIntegrationTest`<br/>`registerAdultClientSuccess`<br/>`registerMinorClientBlocked`<br/>Unicidad 409 |
| **HU-01: Consulta de Clientes** | `SearchClientsUseCase`<br/>(`SearchClientsService`) | `GET /api/v1/clients`<br/>`GET /api/v1/clients/{id}` | `ClientsPage.jsx`<br/>`ClientDetailModal.jsx` | `clients`<br/>`documents` | `ClientIntegrationTest`<br/>Ficha de socio `MAR-XXXXXX` |
| **HU-04: Nómina de Personal** | `StaffAccountUseCase`<br/>(`StaffAccountService`) | `GET /api/v1/accounts` | `StaffPage.jsx` | `accounts`<br/>`account_roles` | `AccountManagementIntegrationTest`<br/>`adminCanListAccounts`<br/>`recepcionForbiddenFromAccounts` (403) |
| **HU-04: Alta de Personal** | `StaffAccountUseCase`<br/>(`StaffAccountService`) | `POST /api/v1/accounts` | `StaffModal.jsx` | `accounts`<br/>`account_roles`<br/>`audit_events` | `AccountManagementIntegrationTest`<br/>`adminCanCreateNewStaffMember` (BCrypt hash) |
| **HU-04: Asignación de Rol** | `StaffAccountUseCase`<br/>(`StaffAccountService`) | `PATCH /api/v1/accounts/{id}/role` | `StaffPage.jsx`<br/>(Modal Rol) | `account_roles`<br/>`sessions`<br/>`audit_events` | `AccountManagementIntegrationTest`<br/>`adminCanUpdateStaffRole` (Revoca sesiones) |
| **HU-04: Protección Último Admin**| `StaffAccountUseCase`<br/>(`StaffAccountService`) | `PATCH /api/v1/accounts/{id}/role`<br/>`PATCH /api/v1/accounts/{id}/inactivate` | `StaffPage.jsx`<br/>(Confirmación) | `accounts`<br/>`account_roles` | `AccountManagementIntegrationTest`<br/>`cannotDemoteLastAdmin`<br/>`cannotInactivateLastAdmin` (HTTP 400) |
| **HU-06: Catálogo de Planes** | `ManagePlanUseCase`<br/>(`ManagePlanService`) | `GET /api/v1/plans`<br/>`GET /api/v1/plans/{id}` | `PlansPage.jsx` | `plans`<br/>`plan_versions` | `PlanIntegrationTest`<br/>`recepcionCanListPlans`<br/>`adminCanCreateAndListPlan` |
| **HU-06: Creación de Plan** | `ManagePlanUseCase`<br/>(`ManagePlanService`) | `POST /api/v1/plans` | `PlanModal.jsx`<br/>`PlansPage.jsx` | `plans`<br/>`plan_versions` (v1)<br/>`audit_events` | `PlanIntegrationTest`<br/>`adminCanCreateAndListPlan`<br/>`recepcionCannotCreatePlan` (403)<br/>Regla 30 días mensual |
| **HU-06: Edición y Versión** | `ManagePlanUseCase`<br/>(`ManagePlanService`) | `PUT /api/v1/plans/{id}` | `PlanModal.jsx` | `plans` (versión + 1)<br/>`plan_versions` (vN snapshot)<br/>`audit_events` | `PlanIntegrationTest`<br/>`updatePlanIncrementsVersionAndInactivates` |
| **HU-06: Control de Estado** | `ManagePlanUseCase`<br/>(`ManagePlanService`) | `PATCH /api/v1/plans/{id}/status` | `PlansPage.jsx`<br/>(Confirmar Inactivar) | `plans`<br/>`audit_events` | `PlanIntegrationTest`<br/>Cambio de estado a `INACTIVO` |

---

## 2. Diagramas de Secuencia Clave

### Secuencia 1: Registro Exitoso de Socio con Generación de Código Único (HU-01)
```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Recepción / Admin
    participant UI as ClientRegisterModal
    participant API as ClientController
    participant UC as RegisterClientService
    participant Gen as MemberCodeGenerator
    participant Port as ClientPersistencePort
    participant DB as PostgreSQL
    participant Audit as AuditService

    Usuario->>UI: Ingresa datos personales (sin casillas de consentimiento ficticias)
    UI->>UI: Valida en caliente mayoría de edad (>= 18 años)
    UI->>API: POST /api/v1/clients (JSON DTO)
    API->>UC: registerAdultClient(Command, Actor)
    UC->>UC: Valida invariantes de dominio (edad >= 18)
    UC->>Port: existsByDocument(type, number)
    Port->>DB: SELECT COUNT(*) FROM documents WHERE type=? AND number=?
    DB-->>Port: 0 (No existe)
    UC->>Gen: generate(firstName, lastName)
    Gen-->>UC: "MAR-8Q2L7K"
    UC->>Port: save(Client, Document)
    Port->>DB: INSERT INTO clients (id, member_code, ...), INSERT INTO documents (...)
    DB-->>Port: OK (Confirmado)
    UC->>Audit: recordEvent(Actor, "CREATE_CLIENT", id, details)
    Audit->>DB: INSERT INTO audit_events (...)
    UC-->>API: Client (Domain Model)
    API-->>UI: 201 Created (ClientResponse con memberCode)
    UI-->>Usuario: Muestra notificación de éxito con código generado
```

---

### Secuencia 2: Rechazo por Documento Duplicado Concurrente (HU-01 - 409 Conflict)
```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Recepción
    participant UI as ClientRegisterModal
    participant API as ClientController
    participant UC as RegisterClientService
    participant Port as ClientPersistencePort
    participant DB as PostgreSQL (Unique Constraint)

    Usuario->>UI: Envía documento existente (ej. DNI 07654321)
    UI->>API: POST /api/v1/clients
    API->>UC: registerAdultClient(Command)
    UC->>Port: existsByDocument("DNI", "07654321")
    Port->>DB: SELECT COUNT(*) FROM documents ...
    DB-->>Port: 1 (Duplicado detectado)
    UC-->>API: DocumentDuplicateException("El documento ya se encuentra registrado...")
    Note over API: GlobalExceptionHandler intercepta DocumentDuplicateException
    API-->>UI: 409 Conflict (Error JSON estandarizado)
    UI-->>Usuario: Muestra alerta: "El documento ya se encuentra registrado con otro socio"
```

---

### Secuencia 3: Control de Acceso RBAC (HU-04 - 401 y 403)
```mermaid
sequenceDiagram
    autonumber
    actor Anonimo as Usuario No Autenticado
    actor Recepcion as Usuario con Rol RECEPCION
    participant Filter as TokenAuthenticationFilter
    participant API as AccountController (/api/v1/accounts)

    Note over Anonimo,API: Intento de consulta sin token
    Anonimo->>API: GET /api/v1/accounts (Sin cabecera Authorization)
    Filter-->>Anonimo: 401 Unauthorized (AuthenticationEntryPoint: "No autenticado")

    Note over Recepcion,API: Intento de consulta de nómina con rol insuficiente
    Recepcion->>Filter: GET /api/v1/accounts (Bearer Token Recepcion)
    Filter->>Filter: Valida token -> Usuario con ROLE_RECEPCION
    Filter->>API: Invoca método protegido con @PreAuthorize("hasAnyRole('ADMIN')")
    API-->>Recepcion: 403 Forbidden (AccessDeniedHandler: "Acceso denegado: rol insuficiente")
```

---

### Secuencia 4: Edición de Plan Tarifario e Instantánea Inmutable (HU-06)
```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrador
    participant UI as PlanModal
    participant API as PlanController
    participant UC as ManagePlanService
    participant Port as PlanPersistencePort
    participant DB as PostgreSQL

    Admin->>UI: Modifica precio de S/ 120.00 a S/ 140.00
    UI->>API: PUT /api/v1/plans/{id} (UpdatePlanRequest)
    API->>UC: updatePlan(id, Command, Actor)
    UC->>Port: findById(id)
    Port->>DB: SELECT * FROM plans WHERE id=?
    DB-->>Port: PlanJpaEntity (version=1)
    UC->>UC: Valida invariantes (precio positivo, 30 días si mensual)
    UC->>Port: save(plan)
    Port->>DB: UPDATE plans SET price_pen=140.00, version=2 WHERE id=? AND version=1
    DB-->>Port: 1 fila afectada -> Version incrementada a 2
    UC->>UC: PlanVersion.snapshot(updatedPlan) -> Instantánea inmutable v2
    UC->>Port: saveVersion(snapshotV2)
    Port->>DB: INSERT INTO plan_versions (plan_id, version_number=2, price_pen=140.00, ...)
    DB-->>Port: OK
    UC-->>API: Plan actualizado a versión 2
    API-->>UI: 200 OK (PlanResponse: version=2, pricePen=140.00)
    UI-->>Admin: Muestra tarjeta con versión 2 actualizada
```

---

### Secuencia 5: Protección Contra Inactivación del Último Administrador (HU-04)
```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrador Único
    participant UI as StaffPage
    participant API as AccountController
    participant UC as StaffAccountService
    participant Port as AccountPersistencePort
    participant DB as PostgreSQL

    Admin->>UI: Solicita inactivar su propia cuenta o cambiar su rol
    UI->>API: PATCH /api/v1/accounts/{id}/inactivate
    API->>UC: inactivateAccount(id, Actor)
    UC->>Port: countActiveAdmins()
    Port->>DB: SELECT COUNT(*) FROM accounts a JOIN account_roles r ... WHERE r.role='ADMIN' AND a.status='ACTIVO'
    DB-->>Port: 1 (Es el único administrador habilitado)
    UC-->>API: LastAdminProtectionException("Operación denegada: no se puede inactivar al último administrador...")
    Note over API: GlobalExceptionHandler traduce a 400 Bad Request
    API-->>UI: 400 Bad Request con mensaje explicativo
    UI-->>Admin: Alerta: "Operación bloqueada para preservar el acceso al sistema"
```

---

## 3. Máquinas de Estado

### Estado de Cuenta de Personal (HU-04)
```mermaid
stateDiagram-v2
    [*] --> ACTIVO : Alta de Colaborador (Hash BCrypt)
    ACTIVO --> INACTIVO : Inactivación con Confirmación (Revoca Sesiones)
    INACTIVO --> ACTIVO : Reactivación por Administrador
    ACTIVO --> ACTIVO : Cambio de Rol (ADMIN <--> RECEPCION) (Revoca Sesiones)
    ACTIVO --> BLOQUEADO_SALVAGUARDA : Intento de inactivar al último ADMIN activo
    BLOQUEADO_SALVAGUARDA --> ACTIVO : Operación rechazada con HTTP 400
```

### Estado de Plan y Ciclo de Vida Tarifario (HU-06)
```mermaid
stateDiagram-v2
    [*] --> ACTIVO : Creación de Plan (Emite Snapshot Versión 1)
    ACTIVO --> INACTIVO : Inactivación por Administrador (Se conserva en catálogo)
    INACTIVO --> ACTIVO : Reactivación por Administrador
    ACTIVO --> ACTIVO : Edición de Condiciones Económicas (Incrementa Versión + Snapshot Inmutable)
    INACTIVO --> [*] : No se permite eliminación física (Preserva integridad referencial)
```
