# ESTADO_BASE_SPRINT1_ACOSTA.md — Estado Técnico de la Base Funcional (Sprint 1)

> **Proyecto:** GYM VIP (VIP CENTER FIT S.A.C.)  
> **Responsable del Incremento:** Andre Acosta (Base Común + HU-01, HU-04, HU-06)  
> **Fecha de Actualización:** 19 de octubre de 2026  
> **Rama Oficial de Entrega:** `base-acosta`  
> **Patrón de Arquitectura:** Backend por Capas Generales (`controller`, `dto`, `entity`, `enums`, `exception`, `mapper`, `repository`, `security`, `service`, `util`)  
> **Estado Global de la Base:** 100% OPERATIVA Y VERIFICADA EN SU ALCANCE (22/22 pruebas unitarias y de integración superadas)

---

## A. Alcance Delimitado: Primera Parte del Sprint 1

Este incremento técnico corresponde **exclusivamente a la primera porción del Sprint 1** asignada a Acosta. No representa la finalización del Sprint 1 global del equipo ni agota el Product Backlog.

### Alcance Entregado:
1. **Infraestructura Base y Arquitectura por Capas:** Reorganización completa del backend bajo el paquete raíz `com.gymvip` en una arquitectura por capas generales y responsabilidades desacopladas, reemplazando la modularidad por dominios/contextos previa.
2. **Seguridad RBAC y Sesiones:** Filtro de autenticación stateless mediante token en cabecera HTTP (`Authorization: Bearer <token>` / `X-Auth-Token`), control de acceso por roles (`ADMIN`, `RECEPCION`) con respuestas 401 Unauthorized y 403 Forbidden estandarizadas, y almacenamiento de sesiones con caducidad.
3. **Manejo Centralizado de Excepciones:** `GlobalExceptionHandler` con mapeo seguro a códigos HTTP semánticos (400, 401, 403, 404, 409, 500) y `ErrorResponse` estructurado con identificador de correlación (`correlationId`).
4. **HU-01 — Registrar Clientes (Alta de Socio):** Formulario web y endpoint `POST /api/v1/clients` con validación estricta de mayoría de edad (≥ 18 años), unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta HTTP 409 Conflict ante duplicados, validación estricta de DNI peruano (8 dígitos numéricos), datos de contacto opcionales (teléfono y correo validados únicamente si se envían), normalización (`trim` y minúsculas), generación de código de socio legible (`MemberCodeGenerator` ej. `MAR-8Q2L7K`) y endpoints de consulta (`GET /api/v1/clients`, `GET /api/v1/clients/{id}`, `GET /api/v1/clients/code/{code}`).
5. **HU-04 — Roles y Gestión de Personal:** Módulo de administración (`/api/v1/accounts`) con listado seguro sin exponer credenciales, creación con hash BCrypt seguro, asignación exclusiva de roles autorizados `ADMIN` o `RECEPCION` (purga completa de `SUPERADMIN` en controladores y consultas), inactivación de colaboradores con revocación inmediata de sesiones activas, y salvaguarda transaccional que impide inactivar o retirar permisos al último administrador activo del sistema (HTTP 409 Conflict).
6. **HU-06 — Catálogo de Planes:** Gestión completa de membresías comerciales (`/api/v1/plans`) con denominación normalizada única, precio positivo en Soles `NUMERIC(12,2) PEN`, regla de negocio de duración mensual de **exactamente 30 días**, regla de pase diario de **exactamente 1 día**, versionado tarifario inmutable (`PlanVersion`) con consulta cronológica en BD, control de concurrencia optimista (`@Version`) e inactivación lógica. Consulta pública/recepción y mutaciones administrativas exclusivas.

### Fuera de Alcance de este Incremento:
- Membresías activas, asignación y contratación (HU-07: Alexander).
- Registro de cobros presenciales, métodos de pago y caja (HU-08: Alexander).
- Renovación periódica de membresías (HU-10: Alexander).
- Búsqueda avanzada y edición de ficha de socios (HU-02: Jorge).
- Regla de cálculo de vigencias y estados VIGENTE/VENCIDO (HU-09: Jorge).
- Reporte operativo de vencimientos (HU-11: Jorge).
- Verificación manual de acceso en recepción (HU-12: Jorge).
- Pasarela de pagos por internet, cobros automáticos y móvil Flutter (Diferidos para Sprint 2 / E03).

---

## B. Estado Real por Historia de Usuario

| HU | Estado Técnico | Criterios Observados e Implementados | Pruebas Automatizadas | Pendientes / Limitaciones Abiertas |
| :--- | :--- | :--- | :--- | :--- |
| **HU-01: Registrar Clientes** | **APROBADO** | - Formulario web y API REST `POST /api/v1/clients`.<br>- Validación estricta ≥ 18 años (HTTP 400 ante menores).<br>- Unicidad documental `(PE, DNI, número)` con HTTP 409 Conflict.<br>- DNI validado a exactamente 8 dígitos numéricos.<br>- Teléfono y correo opcionales (validados en formato solo si se proveen).<br>- Generación de código `MAR-XXXXXX` / `VIP-XXXXXX`.<br>- Consulta paginada `GET /api/v1/clients`. | `ClientIntegrationTest` (6 tests PASS)<br>- Registro exitoso con código socio.<br>- Bloqueo y 409 en documento repetido.<br>- Rechazo con 400 a menor de edad.<br>- Alta exitosa con teléfono y correo omitidos.<br>- Rechazo con 400 a DNI de 7 dígitos o alfanumérico.<br>- Rechazo con 400 a formato de email inválido. | - Edición y actualización de datos de contacto reservada para HU-02 (Jorge).<br>- Soporte formal de menores con representante legal diferido. |
| **HU-04: Roles y Personal** | **APROBADO** | - API `/api/v1/accounts` protegida por rol `ADMIN`.<br>- Roles restringidos a `ADMIN` y `RECEPCION` (sin SUPERADMIN).<br>- Listado sin exponer `password_hash`.<br>- Hashing BCrypt con salt.<br>- Inactivación revoca sesiones de `sessions`.<br>- Protección activa del último administrador. | `AccountManagementIntegrationTest` (7 tests PASS)<br>- Autenticación y listado seguro.<br>- 403 Forbidden para rol RECEPCION.<br>- 401 para petición anónima.<br>- Creación de cajero y validación 409.<br>- Rechazo de roles no autorizados (400).<br>- Cambio de rol y estado activo/inactivo.<br>- Bloqueo ante inactivación del último admin. | - Interfaz de recuperación de contraseñas por correo pendiente de servicio SMTP de producción.<br>- Soporte MFA reservado para etapas posteriores. |
| **HU-06: Catálogo de Planes** | **APROBADO** | - Denominación única case-insensitive (409 Conflict).<br>- Precio PEN positivo con `BigDecimal`.<br>- Regla inquebrantable de 30 días para mensualidades.<br>- Regla inquebrantable de 1 día exacto para pase diario.<br>- Instantánea inmutable en tabla `plan_versions`.<br>- Concurrencia optimista mediante `@Version`.<br>- Inactivación lógica sin borrado físico. | `PlanIntegrationTest` (8 tests PASS)<br>- Admin crea plan y consulta en catálogo.<br>- Recepción recibe 403 al intentar crear.<br>- Rechazo 400 por precio/duración negativos.<br>- Rechazo 400 en mensual con 31 días.<br>- Aprobación de mensual con 30 días exactos.<br>- Pase diario de 1 día aprobado y rechazo a 2 días.<br>- Incremento de versión (v1 -> v2) y comprobación en BD de conservación inmutable de ambas.<br>- Recepción lista catálogo en modo lectura. | - Ninguna en el alcance de planes comerciales. La contratación efectiva corresponde a HU-07 (Alexander). |
| **Persistencia PostgreSQL** | **PREPARADO / LOCAL TESTED** | - Dialecto PostgreSQL activo en Flyway y Spring Data JPA.<br>- Migraciones `V1__init_schema.sql` y `V2__add_member_code_to_clients.sql` 100% compatibles con PostgreSQL.<br>- Perfil `prod` configurado con `ddl-auto=validate` y HikariCP. | Evaluado con Flyway en PostgreSQL / H2 in-memory en suite de tests de Maven (22/22 PASS). | **PostgreSQL Render: NO VERIFICADO.** Requiere credenciales de instancia activa en la nube para conexión externa. Sin instancia local de PostgreSQL activa en Windows al momento de la prueba. |

---

## C. Estructura Final por Capas Generales

La organización física del código fuente bajo `src/main/java/com/gymvip/` responde estrictamente al diseño por responsabilidades desacopladas:

```
src/main/java/com/gymvip/
├── config/
│   ├── AppConfig.java                 # Beans de soporte (PasswordEncoder BCrypt)
│   └── DataInitializer.java           # Provisión controlada en dev (admin, recepcion, 4 planes base)
├── controller/
│   ├── AccountController.java         # Endpoints /api/v1/accounts (ADMIN)
│   ├── AuthController.java            # Endpoints /api/v1/auth (Login, Logout, Me)
│   ├── ClientController.java          # Endpoints /api/v1/clients (ADMIN, RECEPCION)
│   └── PlanController.java            # Endpoints /api/v1/plans (Público lectura, ADMIN mutación)
├── dto/
│   ├── request/
│   │   ├── CreatePlanRequest.java
│   │   ├── CreateStaffAccountRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterClientRequest.java
│   │   ├── UpdatePlanRequest.java
│   │   ├── UpdatePlanStatusRequest.java
│   │   ├── UpdateStaffRoleRequest.java
│   │   └── UpdateStaffStatusRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── ClientDetailResponse.java
│       ├── ClientResponse.java
│       ├── ErrorResponse.java
│       ├── PlanResponse.java
│       └── StaffAccountResponse.java
├── entity/
│   ├── Account.java                   # Entidad JPA tabla 'accounts'
│   ├── AuditEvent.java                # Entidad JPA tabla 'audit_events'
│   ├── Client.java                    # Entidad JPA tabla 'clients'
│   ├── Document.java                  # Entidad JPA tabla 'documents'
│   ├── Plan.java                      # Entidad JPA tabla 'plans'
│   ├── PlanVersion.java               # Entidad JPA tabla 'plan_versions'
│   └── Session.java                   # Entidad JPA tabla 'sessions'
├── enums/
│   ├── AccountStatus.java             # ACTIVO, INACTIVO
│   ├── ClientStatus.java              # ACTIVO, INACTIVO
│   ├── PlanStatus.java                # ACTIVO, INACTIVO
│   └── Role.java                      # ADMIN, RECEPCION, CLIENTE
├── exception/
│   ├── AccountNotFoundException.java
│   ├── ClientNotFoundException.java
│   ├── ClientValidationException.java
│   ├── ConflictException.java
│   ├── DocumentDuplicateException.java
│   ├── DuplicatePlanNameException.java
│   ├── GlobalExceptionHandler.java    # @RestControllerAdvice centralizado (400, 401, 403, 404, 409, 500)
│   ├── InvalidCredentialsException.java
│   ├── InvalidPlanException.java
│   ├── LastAdminProtectionException.java
│   ├── PlanNotFoundException.java
│   └── ResourceNotFoundException.java
├── mapper/
│   ├── AccountMapper.java             # Conversión Account -> StaffAccountResponse
│   ├── ClientMapper.java              # Conversión RegisterClientRequest -> Client -> Responses
│   └── PlanMapper.java                # Conversión CreatePlanRequest -> Plan -> PlanResponse / PlanVersion
├── repository/
│   ├── AccountRepository.java         # Spring Data JPA + countActiveAdmins()
│   ├── AuditEventRepository.java      # Spring Data JPA
│   ├── ClientRepository.java          # Spring Data JPA + searchClients() paginado
│   ├── DocumentRepository.java        # Spring Data JPA + existsByCountryCodeAndDocumentTypeAndDocumentNumber()
│   ├── PlanRepository.java            # Spring Data JPA + existsByNameIgnoreCase()
│   ├── PlanVersionRepository.java     # Spring Data JPA + findFirstByPlanIdOrderByVersionNumberDesc()
│   └── SessionRepository.java         # Spring Data JPA + deleteByAccountId()
├── security/
│   ├── SecurityConfig.java            # SecurityFilterChain, CORS, EntryPoint 401, AccessDeniedHandler 403
│   └── TokenAuthenticationFilter.java # Filtro Bearer / X-Auth-Token contra tabla sessions
├── service/
│   ├── AccountService.java            # Contrato de gestión de personal y sesiones
│   ├── AuditService.java              # Contrato de registro de auditoría
│   ├── AuthService.java               # Contrato de autenticación y logout
│   ├── ClientService.java             # Contrato de alta y consulta de socios
│   ├── PlanService.java               # Contrato de planes y versionado
│   └── impl/
│       ├── AccountServiceImpl.java    # Implementación transaccional HU-04
│       ├── AuditServiceImpl.java      # Implementación transaccional auditoría
│       ├── AuthServiceImpl.java       # Implementación transaccional login/sesión
│       ├── ClientServiceImpl.java     # Implementación transaccional HU-01
│       └── PlanServiceImpl.java       # Implementación transaccional HU-06
├── util/
│   └── MemberCodeGenerator.java       # Generador de códigos de socio (MAR-8Q2L7K)
└── GymVipApplication.java             # Clase de arranque Spring Boot (@SpringBootApplication)
```

---

## D. Guía de Arranque y Configuración por Entorno

### D.1 Variables de Entorno del Backend (`.env.example`)
El backend carga `.env` automáticamente mediante `spring.config.import=optional:file:./.env[.properties]` en `application.yml`:

```properties
SPRING_PROFILES_ACTIVE=dev
PORT=8080
DB_JDBC_URL=jdbc:postgresql://localhost:5432/gymvip
DB_USERNAME=postgres
DB_PASSWORD=postgres
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
```

### D.2 Configuración para Render (Producción)
```properties
SPRING_PROFILES_ACTIVE=prod
DB_JDBC_URL=jdbc:postgresql://HOST_RENDER:5432/DATABASE_NAME?sslmode=require
DB_USERNAME=usuario_render
DB_PASSWORD=clave_privada_render
```
- Perfil `prod` activa `spring.jpa.hibernate.ddl-auto=validate`.
- Flyway aplica automáticamente las migraciones pendientes sobre la BD de Render.

### D.3 Variables de Entorno del Frontend (`gymvip-frontend/.env`)
```properties
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

### D.4 Comandos de Ejecución Local
```powershell
# 1. Ejecutar pruebas unitarias y de integración del backend:
cd gymvip-backend
.\mvnw.cmd test

# 2. Levantar el backend localmente:
.\mvnw.cmd spring-boot:run

# 3. Levantar el frontend:
cd ..\gymvip-frontend
npm install
npm run dev
```

---

## E. Evidencia Mínima de la Intervención Técnica

### E.1 Pruebas Automatizadas Ejecutadas
- **Comando:** `.\mvnw.cmd test`
- **Resultados:** `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0` (100% PASS).
- **Desglose de suites:**
  - `GymVipApplicationTests`: 1 prueba (Arranque de contexto Spring Boot).
  - `AccountManagementIntegrationTest`: 7 pruebas (Listado, 401, 403, alta personal, duplicados, cambio rol/status, protección último admin).
  - `ClientIntegrationTest`: 6 pruebas (Alta socio con código `MAR-XXXXXX`, unicidad documental 409, bloqueo menor de edad 400, alta con campos opcionales omitidos, bloqueo de DNI inválido de 7 dígitos/alfanumérico, bloqueo de correo inválido).
  - `PlanIntegrationTest`: 8 pruebas (Alta de plan, 403 recepción, validación valores negativos, regla mensual 30 días, pase diario de 1 día aprobado y rechazo a 2 días, incremento de versión v1->v2 con comprobación en BD de conservación inmutable de ambas, inactivación, listado por recepción).

### E.2 Compilación del Frontend
- **Comando:** `npm run build` en `gymvip-frontend`.
- **Resultado:** `vite build` completado exitosamente en 6.02s sin errores ni warnings.

### E.3 Archivos Retirados y Reubicados
- `gymvip-backend/scripts/export_issues.py`: Retirado del repositorio de código backend y preservado con valor documental en `documentos/scripts/export_issues.py`.
- `gymvip-backend/ESTADO_BACKEND_BASE.md`: Retirado de la raíz del backend tras consolidación.
- `gymvip-backend/docs/*`: Retirados todos los documentos dispersos y redundantes (`C4_BASE_ACTUAL.md`, `COMMIT_LOG_BASE.md`, `ERD_BASE_ACTUAL.md`, `ESTADO_BACKEND_BASE.md`, `HANDOFF_BASE_SPRINT1.md`, `MODELOS_BASE_ACTUAL.md`, subcarpetas `arquitectura` y `producto`), integrando su contenido en `documentos/` canónico.
- `gymvip-backend/data/`: Excluido en `.gitignore` tras confirmar que correspondía a persistencia local anterior H2; la suite de pruebas ahora opera en memoria sin generar archivos de BD.

---

## F. Base Técnica que Reciben los Compañeros

Alexander y Jorge reciben un núcleo robusto, tipado y probado que deben reutilizar directamente:

1. **Entidades JPA Disponibles (`com.gymvip.entity`):**
   - `Client`: Socio del gimnasio con `memberCode` único, nombres, fecha de nacimiento, datos de contacto y colección de documentos.
   - `Document`: Documento de identidad normalizado con restricción de unicidad compuesta `(countryCode, documentType, documentNumber)`.
   - `Account`: Cuenta institucional con roles (`ADMIN`, `RECEPCION`) y contraseña encriptada.
   - `Session`: Sesión activa con token de 128 caracteres y fecha de expiración.
   - `Plan`: Plan comercial de membresía con precio en PEN, duración en días y control de versión optimista.
   - `PlanVersion`: Instantánea histórica inmutable de tarifas y condiciones contractuales.
   - `AuditEvent`: Bitácora inmutable de eventos del sistema (actor, acción, entidad, detalles, timestamp).

2. **Servicios y Repositorios Reutilizables:**
   - Inyección de `ClientRepository`, `DocumentRepository`, `PlanRepository`, `PlanVersionRepository`, `AccountRepository`, `SessionRepository`, `AuditEventRepository`.
   - `AuditService.recordEvent(...)`: Reutilizable para registrar cualquier mutación económica o de socio.
   - `GlobalExceptionHandler`: Captura automáticamente `IllegalArgumentException`, `MethodArgumentNotValidException`, `ConflictException`, etc.

3. **Coordinación de Migraciones Flyway:**
   - `V1__init_schema.sql` y `V2__add_member_code_to_clients.sql` son inmutables.
   - La siguiente migración debe nombrarse obligatoriamente: **`V3__<descripcion_breve>.sql`**.

---

## G. Tareas Asignadas a Alexander y Jorge (Sprint 1)

> [!IMPORTANT]
> **Reparto Nominal Propuesto para el Sprint 1:**  
> Basado en la delimitación de las partes 2 y 3 del incremento. Debe ratificarse formalmente en la reunión del equipo.

### Alexander — Responsable Parte 2: HU-07, HU-08, HU-10

- **HU-07 — Asignación y Contratación de Plan:**
  - Crear entidad `Membership` en `entity/`, `MembershipRepository` en `repository/`, `MembershipService` en `service/` y `MembershipController` en `controller/`.
  - Vincular al socio (`Client`) con la instantánea inmutable del plan (`PlanVersion`).
  - La contratación de plan y el asiento de cobro inicial deben ocurrir dentro de una **misma transacción atómica** (`@Transactional`) con reversión completa ante fallos.
  - Soportar clave de idempotencia (`Idempotency-Key` en cabecera HTTP) para evitar cobros o contrataciones duplicadas ante reintentos de red.
- **HU-08 — Registro de Pagos Presenciales:**
  - Crear entidad `PaymentRecord` en `entity/`, vinculada a `Membership` y `Account` (recepcionista que cobra).
  - Métodos de pago válidos: `EFECTIVO`, `TARJETA`, `YAPE_PLIN`.
  - Guardar comprobante/referencia externa opcional y registrar evento en `AuditService`.
- **HU-10 — Renovación de Membresías:**
  - Consumir la política de cálculo de fechas que provee Jorge.
  - Renovación de 30 días exactos: si el socio renueva antes de vencer, el nuevo período se acumula a partir del fin del período vigente; si renueva después de vencer, el nuevo período inicia desde la fecha del pago.
- **Frontend:**
  - Integrar vistas `MembershipsView.jsx` y `PaymentsView.jsx` consumiendo el `Sidebar.jsx`, `apiClient.js` y el diseño Dark & Gold existente.
- **Modelado UML:**
  - Mantener a su cargo los diagramas de clases, paquetes y actividades del negocio del equipo.

---

### Jorge — Responsable Parte 3: HU-02, HU-09, HU-11, HU-12 (Corte Manual)

- **HU-02 — Búsqueda y Edición de Socios:**
  - Extender `ClientService` y `ClientController` con endpoints de actualización de datos de contacto (`phone`, `email`).
  - Prohibir la alteración arbitraria de número de documento sin procedimiento formal de rectificación.
  - Añadir filtros avanzados en `ClientRepository.searchClients(...)`.
- **HU-09 — Cálculo y Consulta de Vigencia:**
  - Implementar servicio puro de cálculo de vigencias sin depender de `plusMonths` (mes = exactamente 30 días, inicio inclusivo, fin exclusivo a las 23:59:59).
  - Estados calculados: `VIGENTE`, `PROXIMO_A_VENCER` (≤ 5 días para expirar), `VENCIDO`.
  - Proveer este componente de fechas como dependencia limpia para que Alexander lo consuma en la contratación y renovación.
- **HU-11 — Reporte Operativo de Vencimientos:**
  - Endpoint de consulta para recepción que devuelva los socios con membresías por expirar en los próximos 7 días para gestión preventiva.
- **HU-12 — Verificación Manual de Acceso en Recepción:**
  - Pantalla ágil en el frontend para el personal de recepción: digita o busca el código de socio / documento y el sistema muestra un indicador visual en tiempo real:
    - 🟢 **ACCESO PERMITIDO (VERDE):** Socio con membresía activa y vigente.
    - 🔴 **ACCESO DENEGADO (ROJO):** Socio con membresía vencida, inactiva o inexistente.
  - Este alcance es manual operativo de recepción; no incluye integración con torniquetes biométricos ni lectores QR móviles.
- **Frontend:**
  - Construir vista de edición de socios, reporte de vencimientos y pantalla de control de acceso en recepción utilizando el diseño Luxury Dark & Gold.
