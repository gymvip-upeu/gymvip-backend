# GYM VIP — Backend API (Base Funcional Sprint 1 — Acosta)

> **Repositorio Oficial:** [gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend.git)  
> **Organización:** `gymvip-upeu`  
> **Responsable del Incremento:** Andre Acosta (Base común + HU-01, HU-04, HU-06)  
> **Rama de Desarrollo:** `base-acosta`  
> **Stack Base:** Java 24 / compatible Java 21 LTS, Spring Boot 3.4.3, Spring Data JPA, Spring Security 6, Flyway 10, PostgreSQL / H2 (dev/test), Maven 3.9+.

---

## 1. Alcance Activo y Delimitación

Este repositorio contiene la **base modular funcional** y las tres Historias de Usuario (HU) asignadas a Acosta en el reparto de 3 integrantes para el Sprint 1:
1. **Base Común:** Arquitectura Limpia modular, seguridad JWT/Sesión con RBAC, Flyway migrations, carga desacoplada de `.env`, pooling Hikari y manejo centralizado de excepciones con códigos HTTP estándar (400, 401, 403, 404, 409).
2. **HU-01 — Registrar Clientes:** Alta de socio adulto con validación de edad (≥ 18 años), unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta 409 Conflict ante duplicados, registro legal separado y versionado de consentimientos (Términos, Privacidad Ley N° 29733, Marketing Email opcional, Marketing WhatsApp opcional) y lectura mínima (`GET /api/v1/clients`, `GET /api/v1/clients/{id}`) para consulta y extensión.
3. **HU-04 — Roles y Permisos:** Gestión institucional de personal por rol `ADMIN` (`/api/v1/accounts`): listado de cuentas sin exponer contraseñas, alta con BCrypt, asignación de roles `ADMIN` o `RECEPCION`, inactivación de cuenta con revocación inmediata de sesiones activas, y protección obligatoria del último administrador activo (409 Conflict si se intenta degradar o inactivar).
4. **HU-06 — Configuración de Planes:** Catálogo de planes con denominación normalizada, regla estricta de duración mensual de exactamente 30 días, precios `NUMERIC(12,2) PEN`, versionado inmutable (`PlanVersion`) ante modificaciones de precio o condiciones, e inactivación lógica. Consulta pública/recepción y mutaciones exclusivas para `ADMIN`.

> [!IMPORTANT]
> **Delimitación de Alcance:**  
> Este incremento **NO** incluye las 10 HU del Sprint 1 del equipo ni declara finalizado el Sprint global. Se han retirado del código activo los controladores y flujos de membresías activas (HU-07/08), pagos, pases diarios, accesos y comunicaciones. Las tablas y entidades reservadas se mantienen en el esquema Flyway para que los dos compañeros de equipo continúen su desarrollo sin rehacer la base.

---

## 2. Arquitectura de Software

El backend sigue los principios de **Clean Architecture** y modularidad orientada al dominio dentro de `com.gymvip`:

```
src/main/java/com/gymvip/
├── clients/             # Contexto Clientes (HU-01)
│   ├── api/             # ClientController, DTOs de registro y respuesta
│   ├── application/     # ClientService (orquestación, consentimientos, validación)
│   ├── domain/          # Client, DocumentType, ClientConsent (reglas puras)
│   └── infrastructure/  # ClientRepository, ClientConsentRepository
├── identity/            # Contexto Identidad y Roles (HU-04 y Sesión)
│   ├── api/             # AuthController (login/logout), AccountController (gestión ADMIN)
│   ├── application/     # AuthService, AccountManagementService
│   ├── domain/          # Account, Role, UserSession, DeviceInfo
│   └── infrastructure/  # AccountRepository, SessionRepository
├── plans/               # Contexto Catálogo de Planes (HU-06)
│   ├── api/             # PlanController, DTOs de creación y actualización
│   ├── application/     # PlanService (regla 30 días, versionado inmutable)
│   ├── domain/          # Plan, PlanVersion, PlanStatus
│   └── infrastructure/  # PlanRepository, PlanVersionRepository
├── audit/               # Contexto Auditoría de Acciones
│   ├── domain/          # AuditLog
│   └── infrastructure/  # AuditLogRepository
├── memberships/         # Contratos y modelos reservados para extensión
│   ├── domain/          # Membership, PaymentRecord (extensión Sprint 1)
│   └── infrastructure/  # Repositorios base para compañeros
└── shared/              # Infraestructura común
    ├── exceptions/      # GlobalExceptionHandler (400, 401, 403, 404, 409)
    └── security/        # SecurityConfig, SessionTokenFilter, PasswordEncoder
```

---

## 3. Base de Datos y Migraciones Flyway

Las migraciones son gestionadas mediante **Flyway** (`db/migration/V1__init_schema.sql`), garantizando versionado append-only y compatibilidad con PostgreSQL en producción y H2 en desarrollo.

### Tablas Activas del Alcance
- `accounts`: Cuentas institucionales (`id`, `username`, `email`, `password_hash`, `full_name`, `status`, `created_at`, `updated_at`).
- `account_roles`: Roles asignados (`account_id`, `role`) con FK a `accounts`.
- `user_sessions`: Sesiones activas/revocadas (`id`, `token_hash`, `account_id`, `expires_at`, `revoked_at`, `created_at`).
- `clients`: Socios (`id`, `first_name`, `last_name`, `country_code`, `document_type`, `document_number`, `birth_date`, `email`, `phone`, `status`, `created_at`). Restricción `UK_CLIENT_DOCUMENT (country_code, document_type, document_number)`.
- `client_consents`: Consentimientos Ley N° 29733 (`id`, `client_id`, `type`, `version`, `granted`, `recorded_at`).
- `plans`: Catálogo de planes (`id`, `code`, `name`, `duration_days`, `status`, `created_at`).
- `plan_versions`: Versiones de tarifas (`id`, `plan_id`, `version_number`, `price_pen`, `description`, `effective_from`).
- `audit_logs`: Trazabilidad de seguridad (`id`, `account_id`, `action`, `resource`, `details`, `timestamp`).

### Tablas Reservadas (Preparadas para Extensión de Compañeros)
- `memberships`: Contratos y membresías de clientes (`client_id`, `plan_id`, vigencias, estado).
- `payment_records`: Registro de pagos y transacciones financieras.

---

## 4. Endpoints y Seguridad (Matriz RBAC)

Todos los endpoints bajo `/api/v1` implementan autenticación basada en sesión/token con control estricto:

| Método | Endpoint | Rol Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Público | Autenticación con credenciales y creación de sesión |
| `POST` | `/api/v1/auth/logout` | Autenticado | Cierre de sesión y revocación de token |
| `GET` | `/api/v1/auth/me` | Autenticado | Información del usuario en sesión |
| `POST` | `/api/v1/clients` | `RECEPCION`, `ADMIN` | Registro de socio adulto y consentimientos (HU-01) |
| `GET` | `/api/v1/clients` | `RECEPCION`, `ADMIN` | Consulta paginada/listado de clientes |
| `GET` | `/api/v1/clients/{id}` | `RECEPCION`, `ADMIN` | Ficha básica del socio registrado |
| `GET` | `/api/v1/accounts` | `ADMIN`, `SUPERADMIN` | Listado del personal y estados de cuenta (HU-04) |
| `POST` | `/api/v1/accounts` | `ADMIN`, `SUPERADMIN` | Alta de personal con rol inicial |
| `PUT` | `/api/v1/accounts/{id}/role` | `ADMIN`, `SUPERADMIN` | Cambio de rol (ADMIN / RECEPCION) |
| `PUT` | `/api/v1/accounts/{id}/status` | `ADMIN`, `SUPERADMIN` | Inactivación con revocación de sesiones |
| `GET` | `/api/v1/plans` | `RECEPCION`, `ADMIN` | Catálogo de planes activos (HU-06) |
| `POST` | `/api/v1/plans` | `ADMIN`, `SUPERADMIN` | Alta de nuevo plan y versión inicial |
| `PUT` | `/api/v1/plans/{id}` | `ADMIN`, `SUPERADMIN` | Modificación con nueva `PlanVersion` inmutable |
| `PATCH`| `/api/v1/plans/{id}/status` | `ADMIN`, `SUPERADMIN` | Inactivación lógica del plan |

---

## 5. Configuración de Variables de Entorno

El backend carga explícitamente variables desde el archivo local `.env` ubicado en la raíz del proyecto mediante la directiva de Spring Boot:
```properties
spring.config.import=optional:file:./.env[.properties]
```

### Variables Requeridas (`.env.example`)
```env
PORT=8080
SPRING_PROFILES_ACTIVE=dev
DB_JDBC_URL=jdbc:postgresql://HOST_EXTERNO_RENDER:5432/gymvip_db?sslmode=require
DB_USERNAME=gymvip_user
DB_PASSWORD=secret_password_aqui
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

> [!WARNING]
> **Despliegue en Render:**  
> El archivo `.env` está estrictamente ignorado en Git para proteger secretos. En Render, las variables deben configurarse directamente en el panel de **Environment Variables** del Web Service con los mismos nombres de variable. La conexión externa requiere TLS (`sslmode=require`).

---

## 6. Ejecución y Pruebas Locales

### Requisitos
- Java 21 LTS o Java 24
- Maven 3.9+ (o usar `./mvnw` incluido)

### Compilar y Ejecutar Pruebas
```powershell
# En Windows PowerShell
./mvnw clean test
```
**Resultado de Verificación Ejecutada:**
- 16 pruebas automatizadas ejecutadas (`ClientIntegrationTest`, `AccountManagementIntegrationTest`, `PlanIntegrationTest`, `GymVipApplicationTests`).
- **Fallos:** 0, **Errores:** 0, **Skipped:** 0.

### Iniciar Servidor en Modo Desarrollo
```powershell
./mvnw spring-boot:run
```
El servidor iniciará por defecto en el puerto `8080` (o el configurado en `PORT`).

---

## 7. Provisionamiento Inicial de Administrador

Por motivos de seguridad, la base no utiliza credenciales predeterminadas hardcodeadas en producción.  
En el entorno de desarrollo y pruebas (`dev`), el `DataInitializer` provisiona una cuenta administrativa de ensayo para verificar el flujo completo de arranque:
- **Usuario:** `admin`
- **Contraseña:** `Admin123!`
- **Rol:** `ROLE_ADMIN`

Para entornos de producción (Render PostgreSQL), la cuenta de administración inicial debe provisionarse mediante un script SQL único o variable administrativa privada ejecutada una sola vez.

---

## 8. Puntos de Extensión para Compañeros de Equipo

Los dos compañeros que asumen la extensión del Sprint 1 encontrarán la base preparada con los siguientes puntos de inserción:
1. **HU-02 (Ficha y Contacto Extendido):** Reutilizar `ClientService` y agregar endpoints en `ClientController` bajo `/api/v1/clients/{id}/contacts`.
2. **HU-07 & HU-08 (Membresías y Pagos):** Conectar la lógica de negocio en el paquete reservado `com.gymvip.memberships` utilizando las tablas ya creadas `memberships` y `payment_records` en `V1__init_schema.sql`.
3. **Módulo de Accesos / Turnos:** Agregar endpoints que consulten `SessionRepository` y `MembershipRepository` sin modificar la arquitectura de seguridad existente.
