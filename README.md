# GYM VIP — Backend API (Base Funcional Sprint 1 — Acosta)

> **Repositorio Oficial:** [gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend.git)  
> **Organización:** `gymvip-upeu`  
> **Responsable del Incremento:** Andre Acosta (Base común + HU-01, HU-04, HU-06)  
> **Rama de Desarrollo:** `base-acosta`  
> **Stack Base:** Java 21 LTS (ejecutable en Java 21-24), Spring Boot 3.4.3, Spring Data JPA, Spring Security 6, Flyway 10, PostgreSQL / H2 (dev/test), Maven Wrapper 3.9+.

---

## 1. Alcance Activo y Delimitación

Este repositorio contiene la **base modular funcional** y las tres Historias de Usuario (HU) asignadas a Acosta en el reparto de 3 integrantes para el Sprint 1:
1. **Base Común:** Clean Architecture modular por contextos, seguridad RBAC estricta (401/403), Flyway migrations, carga explícita de `.env` (`spring.config.import=optional:file:./.env[.properties]`), pooling HikariCP y manejo centralizado de excepciones con códigos HTTP estándar (400, 401, 403, 404, 409).
2. **HU-01 — Registrar Clientes:** Alta de socio adulto con validación de edad (≥ 18 años), unicidad documental compuesta `(type, number)` con respuesta 409 Conflict ante duplicados, generación de código de socio legible con prefijo y sufijo aleatorio (`MAR-8Q2L7K`) mediante `MemberCodeGenerator` y lectura mínima (`GET /api/v1/clients`, `GET /api/v1/clients/{id}`) para consulta y ficha. Se retiraron las casillas de términos y casillas de privacidad conforme a la última decisión de diseño UX.
3. **HU-04 — Roles y Permisos:** Gestión institucional de personal por rol `ADMIN` (`/api/v1/accounts`): listado de cuentas sin exponer contraseñas, alta con hash seguro BCrypt (con salt), asignación de roles `ADMIN` o `RECEPCION`, inactivación de cuenta con revocación inmediata de sesiones activas, y salvaguarda activa del último administrador habilitado (HTTP 400).
4. **HU-06 — Catálogo de Planes:** Catálogo de planes con denominación normalizada, regla estricta de duración mensual de exactamente **30 días**, precios en Soles `NUMERIC(12,2) PEN`, versionado inmutable (`PlanVersion`) ante modificaciones de precio o condiciones, control de concurrencia optimista (`@Version`), e inactivación lógica. Consulta pública/recepción y mutaciones exclusivas para `ADMIN`.

> [!IMPORTANT]
> **Delimitación de Alcance:**  
> Este incremento **NO** incluye las 10 HU del Sprint 1 del equipo ni declara finalizado el Sprint global. Se han retirado del código activo los flujos de membresías activas, pagos, pases diarios, accesos y comunicaciones. Las tablas y entidades reservadas se mantienen en el esquema Flyway para que los dos compañeros continúen su desarrollo sin rehacer la base.

---

## 2. Organización Clean Architecture en Backend

El backend implementa Clean Architecture con separación estricta entre capas y contextos dentro del paquete raíz `com.gymvip`:

```
src/main/java/com/gymvip/
├── audit/
│   ├── application/ (AuditService, port/in/AuditUseCase, port/out/AuditPersistencePort)
│   ├── domain/model/ (AuditEvent)
│   └── infrastructure/persistence/ (entity/AuditEventJpaEntity, repository, adapter)
├── clients/
│   ├── api/ (ClientController, dto/RegisterClientRequest, ClientResponse, mapper)
│   ├── application/ (usecase/RegisterClientService, SearchClientsService, port/in, port/out)
│   ├── domain/ (model/Client, Document, MemberCodeGenerator, exception/ClientValidationException, etc.)
│   └── infrastructure/persistence/ (entity/ClientJpaEntity, DocumentJpaEntity, repository, adapter, mapper)
├── identity/
│   ├── api/ (AccountController, AuthController, dto, mapper)
│   ├── application/ (usecase/AuthService, StaffAccountService, port/in, port/out)
│   ├── domain/ (model/Account, Session, exception/LastAdminProtectionException, etc.)
│   └── infrastructure/persistence/ (entity/AccountJpaEntity, SessionJpaEntity, repository, adapter, mapper)
├── plans/
│   ├── api/ (PlanController, dto/CreatePlanRequest, UpdatePlanRequest, UpdateStatusRequest, mapper)
│   ├── application/ (usecase/ManagePlanService, port/in, port/out)
│   ├── domain/ (model/Plan, PlanVersion, exception/DuplicatePlanNameException, etc.)
│   └── infrastructure/persistence/ (entity/PlanJpaEntity, PlanVersionJpaEntity, repository, adapter, mapper)
└── shared/
    ├── config/ (DataInitializer, ExplicitEnvLoader)
    ├── exceptions/ (GlobalExceptionHandler -> 400, 401, 403, 404, 409)
    └── security/ (SecurityConfig, TokenAuthenticationFilter)
```

**Regla de Dependencias:**
- `domain`: Modelos, invariantes y excepciones de negocio puras. Sin Spring, JPA ni HTTP.
- `application`: Casos de uso y puertos de entrada/salida. Depende exclusivamente del dominio.
- `infrastructure`: Adaptadores que implementan los puertos de persistencia con Spring Data JPA.
- `api`: Controladores REST y DTOs de transporte HTTP.

---

## 3. Base de Datos, Flyway y Variables de Entorno

### 3.1 Carga Explícita del archivo `.env`
Spring Boot carga el archivo `.env` de la raíz del proyecto mediante la configuración en `application.yml`:
```yaml
spring:
  config:
    import: "optional:file:./.env[.properties]"
```

### 3.2 Variables de Entorno Mínimas (`.env.example`)
```properties
SPRING_PROFILES_ACTIVE=dev
PORT=8080
DB_JDBC_URL=jdbc:h2:file:./data/gymvip;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE
DB_USERNAME=sa
DB_PASSWORD=
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
```

### 3.3 Conexión a PostgreSQL en Render
Para conectar a Render externo:
```properties
SPRING_PROFILES_ACTIVE=prod
DB_JDBC_URL=jdbc:postgresql://HOST_EXTERNO_RENDER:5432/NOMBRE_BD?sslmode=require
DB_USERNAME=usuario_render
DB_PASSWORD=secreto_render
```
*Estado actual Render: PREPARADO (.env configurado) — Render NO VERIFICADO (requiere credenciales de instancia activa).*

### 3.4 Migraciones Flyway
- `V1__init_schema.sql`: Esquema inicial (cuentas, roles, sesiones, clientes, documentos, planes, plan_versions, audit_events, memberships legacy, payment_records legacy).
- `V2__add_member_code_to_clients.sql`: Agrega columna `member_code VARCHAR(30)` con índice único `uk_clients_member_code` e índice único compuesto en `plan_versions (plan_id, version_number)`.

---

## 4. Instrucciones de Compilación y Ejecución

### 4.1 Ejecutar Suite de Pruebas
```bash
./mvnw test
```
*Resultado:* 16/16 pruebas de integración ejecutadas y aprobadas (100% PASS).

### 4.2 Iniciar Backend Localmente
```bash
./mvnw spring-boot:run
```
El servidor arrancará por defecto en `http://localhost:8080`.

### 4.3 Cuentas Iniciales para Pruebas
- **Administrador:** `admin@vipcenterfit.pe` / `Admin123!` (Rol: `ADMIN`)
- **Recepción:** `recepcion@vipcenterfit.pe` / `Recepcion123!` (Rol: `RECEPCION`)
