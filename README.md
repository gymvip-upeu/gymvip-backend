# GYM VIP — Backend API (Base Funcional Sprint 1 — Acosta)

> **Repositorio Oficial:** [gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend.git)  
> **Organización:** `gymvip-upeu`  
> **Responsable del Incremento:** Andre Acosta (Base común + HU-01, HU-04, HU-06)  
> **Rama de Entrega:** `base-acosta`  
> **Stack Base:** Java 21 LTS (ejecutable en 21–24), Spring Boot 3.4.3, Spring Data JPA, Spring Security 6, Flyway 10, PostgreSQL / H2 (en memoria para tests), Maven Wrapper 3.9+.

---

## 1. Alcance Activo y Delimitación

Este repositorio contiene la **base funcional por capas** y las tres Historias de Usuario (HU) asignadas a Acosta en el reparto para el Sprint 1:
1. **Base Común:** Arquitectura backend organizada por capas generales (`controller`, `dto`, `entity`, `enums`, `exception`, `mapper`, `repository`, `security`, `service`, `util`), seguridad RBAC estricta (401/403), migraciones reproducibles con Flyway, carga explícita de `.env` (`spring.config.import=optional:file:./.env[.properties]`), pooling HikariCP y manejo centralizado de excepciones con códigos HTTP estándar (400, 401, 403, 404, 409).
2. **HU-01 — Registrar Clientes:** Alta de socio adulto con validación de edad (≥ 18 años), unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta 409 Conflict ante duplicados, generación de código de socio legible con prefijo y sufijo aleatorio (`MAR-8Q2L7K`) mediante `MemberCodeGenerator`, y lectura paginada/ficha (`GET /api/v1/clients`, `GET /api/v1/clients/{id}`, `GET /api/v1/clients/code/{code}`).
3. **HU-04 — Roles y Permisos:** Gestión institucional de personal por rol `ADMIN` (`/api/v1/accounts`): listado de cuentas sin exponer contraseñas, alta con hash seguro BCrypt (con salt), asignación de roles `ADMIN` o `RECEPCION`, inactivación de cuenta con revocación inmediata de sesiones activas, y salvaguarda activa del último administrador habilitado (HTTP 409 Conflict).
4. **HU-06 — Catálogo de Planes:** Catálogo de planes con denominación normalizada única, regla estricta de duración mensual de exactamente **30 días**, precios en Soles `NUMERIC(12,2) PEN`, versionado inmutable (`PlanVersion`) ante modificaciones de precio o condiciones, control de concurrencia optimista (`@Version`), e inactivación lógica. Consulta pública/recepción y mutaciones exclusivas para `ADMIN`.

> [!IMPORTANT]
> **Delimitación de Alcance:**  
> Este incremento **NO** incluye las 10 HU del Sprint 1 del equipo ni declara finalizado el Sprint global. Las tareas de asignación/contratación de membresías y cobro presencial (HU-07, HU-08, HU-10) corresponden a Alexander, y la búsqueda avanzada, edición de socios, cálculo de vigencias y verificación de acceso (HU-02, HU-09, HU-11, HU-12) corresponden a Jorge. Para el detalle exhaustivo, consulte [docs/ESTADO_BASE_SPRINT1_ACOSTA.md](docs/ESTADO_BASE_SPRINT1_ACOSTA.md).

---

## 2. Estructura por Capas Generales

El backend implementa una arquitectura por capas desacopladas bajo el paquete raíz `com.gymvip`:

```
src/main/java/com/gymvip/
├── config/             # Configuración general y provisión de datos iniciales
├── controller/         # Controladores REST (/api/v1/clients, accounts, auth, plans)
├── dto/
│   ├── request/        # DTOs de entrada y validaciones Bean Validation
│   └── response/       # DTOs de salida y ErrorResponse
├── entity/             # Entidades JPA (Client, Document, Account, Session, Plan, PlanVersion, AuditEvent)
├── enums/              # Enumeraciones tipadas (Role, AccountStatus, ClientStatus, PlanStatus)
├── exception/          # Excepciones semánticas y GlobalExceptionHandler
├── mapper/             # Mappers manuales DTO <-> Entidad
├── repository/         # Interfaces Spring Data JPA
├── security/           # Filtro TokenAuthenticationFilter y SecurityConfig RBAC
├── service/            # Interfaces de servicios de negocio
│   └── impl/           # Implementaciones transaccionales (@Transactional)
├── util/               # Utilidades de negocio (MemberCodeGenerator)
└── GymVipApplication.java
```

---

## 3. Base de Datos, Flyway y Variables de Entorno

### 3.1 Carga Explícita del archivo `.env`
Spring Boot carga el archivo `.env` de la raíz del proyecto mediante la directiva en `application.yml`:
```yaml
spring:
  config:
    import: "optional:file:./.env[.properties]"
```

### 3.2 Variables de Entorno Mínimas (`.env.example`)
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

### 3.3 Conexión a PostgreSQL en Render
```properties
SPRING_PROFILES_ACTIVE=prod
DB_JDBC_URL=jdbc:postgresql://HOST_EXTERNO_RENDER:5432/NOMBRE_BD?sslmode=require
DB_USERNAME=usuario_render
DB_PASSWORD=secreto_render
```
*Estado actual Render: PREPARADO (.env y perfil prod con ddl-auto=validate y Flyway) — Render NO VERIFICADO (requiere credenciales de instancia activa).*

### 3.4 Migraciones Flyway
- `V1__init_schema.sql`: Esquema base (cuentas, roles, sesiones, clientes, documentos, planes, plan_versions, audit_events, memberships legacy, payment_records legacy).
- `V2__add_member_code_to_clients.sql`: Agrega columna `member_code VARCHAR(30)` con índice único `uk_clients_member_code` e índice único compuesto en `plan_versions (plan_id, version_number)`.
- *Siguiente migración coordinada para el equipo:* **`V3__...sql`**.

---

## 4. Instrucciones de Compilación y Ejecución

### 4.1 Ejecutar Suite de Pruebas
```bash
.\mvnw.cmd test
```
*Resultado:* 22/22 pruebas unitarias y de integración superadas (100% PASS). La suite opera en memoria (`jdbc:h2:mem:gymvip_test;MODE=PostgreSQL`) sin alterar archivos locales ni requerir base externa.

### 4.2 Iniciar Backend Localmente
```bash
.\mvnw.cmd spring-boot:run
```
El servidor arrancará por defecto en `http://localhost:8080`.

### 4.3 Cuentas Iniciales para Pruebas en Desarrollo (`dev`)
- **Administrador:** `admin@vipcenterfit.pe` / `Admin123!` (Rol: `ADMIN`)
- **Recepción:** `recepcion@vipcenterfit.pe` / `Recepcion123!` (Rol: `RECEPCION`)

---

## 5. Documentación Canónica

El documento único consolidado de estado técnico dentro del backend se ubica en:
👉 **[docs/ESTADO_BASE_SPRINT1_ACOSTA.md](docs/ESTADO_BASE_SPRINT1_ACOSTA.md)**

La documentación integral de arquitectura, análisis, diseño y producto de GYM VIP se centraliza en la carpeta canónica de la raíz del workspace: `GYMVIP/documentos/` (`ARCHITECTURE.md`, `C4_BASE_ACTUAL.md`, `ERD_BASE_ACTUAL.md`, `MODELOS_BASE_ACTUAL.md`, `PRD.md`, `HANDOFF_BASE_SPRINT1.md`).
