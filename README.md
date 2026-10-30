# GYM VIP — Backend API

> **Sistema:** GYM VIP (VIP Center Fit) — Gestión Integral de Gimnasio Luxury Fitness<br>
> **Repositorio:** [gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend.git)<br>
> **Rama documentada:** `feature/ajustes-diseno-documentacion-seguridad`<br>
> **Estado del incremento:** Base consolidada de Sprint 1, compatibilidad Flyway V1-V5, pruebas unitarias 9/9 y arquitectura por capas.

---

## 1. Presentación y Estado Actual

GYM VIP es una plataforma integral diseñada para la administración y control operativo de un centro de entrenamiento de alta gama (*luxury fitness*). Este repositorio contiene la **API REST del backend**, desarrollada con Spring Boot y PostgreSQL, responsable de la lógica de negocio, reglas de cobro, contratos de membresía, auditoría y control de acceso.

La rama actual **`feature/s1-jorge-hu02-hu09-hu11-hu12`** representa el estado funcional más avanzado del Sprint 1 en el repositorio. Integra de forma acumulativa y lineal las contribuciones de los tres desarrolladores del equipo:
1. **Base técnica y núcleo inicial (André Acosta):** Configuración de arquitectura en capas, seguridad stateless, motor PostgreSQL, catálogo de planes (HU-06), gestión de personal (HU-04) y alta de clientes (HU-01).
2. **Membresías y cobros atómicos (Alexander):** Contratación de membresías (HU-07), cobro presencial con constancia e idempotencia (HU-08) y renovaciones con continuidad de fechas (HU-10).
3. **Auditoría, vigencias y control de acceso (Jorge):** Edición concurrente de clientes con auditoría campo por campo (HU-02), cálculo de bloques de cobertura pagados (HU-09), reporte de vencimientos (HU-11) y verificación manual de ingreso (HU-12).

> [!NOTE]
> Las ramas `main` y `develop` conservan el árbol inicial vacío según la estrategia de integración diferida del proyecto. El trabajo funcional completo de este sprint se encuentra en esta rama.

---

## 2. Funcionalidades por Historia de Usuario y Responsable

A continuación se resume el alcance implementado en el código de esta rama:

| HU | Responsable | Funcionalidad Disponible | Ubicación Principal en el Código |
| :--- | :--- | :--- | :--- |
| **Base Común** | André | Seguridad stateless con tokens Bearer/X-Auth-Token, roles `ADMIN` y `RECEPCION`, manejo centralizado de excepciones con `GlobalExceptionHandler`, migraciones Flyway y conexión PostgreSQL. | `com.gymvip.security`, `com.gymvip.exception`, `db/migration/V1__init_schema.sql` |
| **HU-01** | André | Alta y consulta de socios. Validación de mayoría de edad (≥ 18 años), DNI de 8 dígitos, unicidad documental compuesta `(country, type, number)` y generación determinista de código de socio (ej. `MAR-8Q2L7K`). | `ClientController`, `ClientService`, `MemberCodeGenerator`, `V2__add_member_code_to_clients.sql` |
| **HU-04** | André | Administración de colaboradores y credenciales. Creación con hashing BCrypt, cambio de roles `ADMIN`/`RECEPCION`, desactivación lógica, protección del último administrador activo y revocación de sesiones. Exclusivo `ADMIN`. | `AccountController`, `AccountService`, `AccountRepository` |
| **HU-06** | André | Catálogo tarifario de planes en Soles (PEN). Regla estricta de 30 días para planes mensuales y 1 día para pases diarios. Versionado tarifario inmutable (`plan_versions`) que congela condiciones de contratos previos. Concurrencia optimista. | `PlanController`, `PlanService`, `PlanVersion`, `PlanRepository` |
| **HU-07** | Alexander | Asignación y contratación de membresías. Cotización (`/quote`) basada en el precio de la versión activa del plan, resumen comercial congelado y calendario de vigencia. | `MembershipController`, `MembershipService`, `MembershipPeriod`, `V3__...`, `V4__...` |
| **HU-08** | Alexander | Cobro presencial atómico en efectivo, tarjeta o billetera digital (PEN). Generación de constancia interna (`VIP-...`), registro de operador y fecha UTC. Soporte de idempotencia mediante cabecera `Idempotency-Key`. | `MembershipController`, `MembershipServiceImpl`, `MembershipSaleRepository` |
| **HU-10** | Alexander | Renovación anticipada (añade 30 días continuos al vencimiento previo) o vencida (inicia en fecha de cobro). Historial de periodos y pagos del socio. Admite cambio de plan para el nuevo periodo. | `MembershipController`, `MembershipServiceImpl`, `MembershipDates` |
| **HU-02** | Jorge | Edición de datos de clientes (nombres, apellidos, documento, teléfono y correo) para `ADMIN` y `RECEPCION`. Control optimista de concurrencia (`version`). Historial de cambios campo por campo (`client_changes`) consultable exclusivamente por `ADMIN`. | `ClientEditingController`, `ClientEditingService`, `Client.java`, `V5__client_version_and_change_history.sql` |
| **HU-09** | Jorge | Cálculo de vigencia en servidor bajo zona horaria `America/Lima` e intervalo de inicio incluido / fin excluido. Consolidación de periodos pagados contiguos en bloques de cobertura. Estados: `VIGENTE`, `POR_VENCER`, `VENCIDO`, `CANCELADA`, `PENDIENTE`, `SIN_MEMBRESIA`. Sin prórrogas automáticas. | `CoverageController`, `CoverageService`, `CoveragePolicy`, `CoverageRepository` |
| **HU-11** | Jorge | Listado paginado y filtrable de cobertura por socio, plan, estado y rango de fechas. Orden de atención por urgencia (vencimiento más próximo primero). Filtro por defecto de alertas (`POR_VENCER` y `VENCIDO`). | `CoverageController`, `CoverageService` |
| **HU-12** | Jorge | Verificación manual de ingreso en recepción (`/api/v1/coverage/clients/{id}`). Validación autoritativa en servidor que rechaza clientes inactivos, sin pago o vencidos. | `CoverageController`, `CoverageService` |

### Delimitación de Alcance (Fuera de este Incremento)
- No incluye aplicación móvil (KMP/Compose Multiplatform).
- No incluye lectores QR, cámara web, torniquetes ni registro automatizado de asistencia física.
- No incluye pasarelas de pago electrónico online (Stripe, Culqi, etc.) ni transacciones automáticas con terminales POS físicos.
- No incluye módulos de ventas de cafetería, inventario, reservas de clases ni reportes estadísticos de Sprint 2.

---

## 3. Arquitectura y Tecnologías

### 3.1 Stack Tecnológico
- **Lenguaje:** Java 21 LTS (según `<java.version>21</java.version>` en `pom.xml`).
- **Framework:** Spring Boot 3.4.3 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`).
- **Base de Datos:** PostgreSQL 17.x con driver `org.postgresql:postgresql`.
- **Migraciones:** Flyway 10.20.1 (`flyway-core`, `flyway-database-postgresql`).
- **Pool de Conexiones:** HikariCP (`maximum-pool-size: 5`, `minimum-idle: 1`).
- **Seguridad:** Spring Security 6 con autenticación stateless mediante tokens en cabecera HTTP (`Authorization: Bearer <token>` o `X-Auth-Token`) y hashing BCrypt.
- **Herramienta de Construcción:** Apache Maven 3.9.10 mediante Maven Wrapper (`mvnw.cmd` / `./mvnw`).

### 3.2 Estilo Arquitectónico
El proyecto sigue una **arquitectura por capas que aplica principios de Clean Architecture**, separando responsabilidades sin introducir acoplamiento innecesario:
```
src/main/java/com/gymvip/
├── config/         # Configuración de beans, CORS, Clock y seguridad de base de datos
├── controller/     # Controladores REST y autorización @PreAuthorize
├── dto/            # Objetos de transferencia de datos (request y response)
├── entity/         # Entidades JPA mapeadas a tablas de PostgreSQL
├── enums/          # Enumeraciones de dominio (roles, estados, tipos de documento)
├── exception/      # Excepciones de negocio y GlobalExceptionHandler
├── mapper/         # Transformación desacoplada entre entidades y DTOs
├── repository/     # Interfaces Spring Data JPA y consultas nativas de cobertura
├── security/       # Filtros de autenticación, proveedor de tokens y contexto de usuario
├── service/        # Lógica de negocio transaccional (@Transactional)
└── util/           # Utilidades puras (cálculo de fechas, políticas de cobertura, códigos)
```

### 3.3 Migraciones Flyway
El esquema de base de datos se versiona estrictamente mediante scripts Flyway en `src/main/resources/db/migration/`:
- `V1__init_schema.sql`: Cuentas de personal, roles, sesiones, clientes, documentos, planes, versiones tarifarias y tabla genérica de auditoría.
- `V2__add_member_code_to_clients.sql`: Columna `member_code` única para identificación amigable de clientes.
- `V3__membership_periods_and_sales.sql`: Tablas de contratos de membresía (`membership_periods`) y ventas/cobros (`sales`).
- `V4__membership_commercial_terms.sql`: Congelamiento de términos comerciales y condiciones pactadas al contratar.
- `V5__client_version_and_change_history.sql`: Control de concurrencia optimista (`version`) en tabla `clients` y tabla de auditoría detallada `client_changes`.

Hibernate está configurado con `ddl-auto: validate`, impidiendo alteraciones estructurales automáticas fuera de Flyway.

### 3.4 Endpoints Principales y Restricciones de Acceso

| Método | Endpoint | Roles Permitidos | Propósito |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Público | Autenticación con credenciales y emisión de token |
| `POST` | `/api/v1/clients` | `ADMIN`, `RECEPCION` | Alta de nuevo socio |
| `GET` | `/api/v1/clients` | `ADMIN`, `RECEPCION` | Listado paginado de clientes con filtro de búsqueda |
| `GET` | `/api/v1/clients/{id}` | `ADMIN`, `RECEPCION` | Consulta detallada de ficha de socio con versión |
| `PUT` | `/api/v1/clients/{id}` | `ADMIN`, `RECEPCION` | Edición de datos del socio con control optimista |
| `GET` | `/api/v1/clients/{id}/changes` | **Solo `ADMIN`** | Historial de modificaciones de la ficha del socio (auditoría) |
| `GET` | `/api/v1/accounts` | **Solo `ADMIN`** | Listado de colaboradores del gimnasio |
| `POST` | `/api/v1/accounts` | **Solo `ADMIN`** | Registro de nuevo colaborador |
| `PATCH`| `/api/v1/accounts/{id}/role` | **Solo `ADMIN`** | Modificación de rol (`ADMIN` / `RECEPCION`) |
| `PATCH`| `/api/v1/accounts/{id}/status`| **Solo `ADMIN`** | Activar o inactivar colaborador |
| `GET` | `/api/v1/plans` | `ADMIN`, `RECEPCION` | Catálogo de planes activos para venta |
| `POST` | `/api/v1/plans` | **Solo `ADMIN`** | Creación de nuevo plan |
| `PUT` | `/api/v1/plans/{id}` | **Solo `ADMIN`** | Edición de tarifa de plan (genera nueva versión inmutable) |
| `POST` | `/api/v1/memberships/quote` | `ADMIN`, `RECEPCION` | Cotización formal de contratación o renovación |
| `POST` | `/api/v1/memberships/sales` | `ADMIN`, `RECEPCION` | Cobro y registro atómico de membresía con clave de idempotencia |
| `GET` | `/api/v1/coverage/clients/{id}`| `ADMIN`, `RECEPCION` | Verificación de vigencia y acceso manual de un socio |
| `GET` | `/api/v1/coverage` | `ADMIN`, `RECEPCION` | Reporte paginado de vencimientos y alertas |

---

## 4. Requisitos y Configuración Segura

### 4.1 Requisitos Previos
- **Java:** JDK 21 o superior instalado y configurado en `JAVA_HOME`.
- **Base de Datos:** Instancia de PostgreSQL 17.x en ejecución.

### 4.2 Variables de Entorno (`.env`)
El backend carga automáticamente variables desde el archivo `.env` en la raíz del proyecto mediante:
```yaml
spring:
  config:
    import: "optional:file:./.env[.properties]"
```

Plantilla de configuración recomendada (`.env.example`):
```properties
# Perfil activo: dev para desarrollo local, prod para producción
SPRING_PROFILES_ACTIVE=dev
PORT=8081

# Conexión principal PostgreSQL (usar variables reales de entorno fuera del repo)
DB_JDBC_URL=jdbc:postgresql://localhost:5432/gymvip
DB_USERNAME=gymvip_user
DB_PASSWORD=TU_CONTRASENA_AQUI

# Esquema y pool para desarrollo
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1

# Configuración de pruebas (aislada obligatoria)
TEST_DB_SCHEMA=gymvip_test_jorge
# TEST_DB_JDBC_URL=jdbc:postgresql://... (opcional si difiere de DB_JDBC_URL)

# Orígenes CORS permitidos
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000

# Días de anticipación para estado POR_VENCER (1 a 30, valor por defecto: 7)
MEMBERSHIP_UPCOMING_DAYS=7

# Cuentas iniciales generadas por DataInitializer en dev
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
```

> [!CAUTION]
> Nunca incluya contraseñas reales ni cadenas de conexión con credenciales en commits de Git. Mantenga el archivo `.env` ignorado por Git en todo momento.

---

## 5. Cómo Ejecutar y Probar

### 5.1 Compilar y Ejecutar Pruebas Automatizadas
Para ejecutar las pruebas que no requieren base de datos externa (política de cobertura, fechas e isolación de esquemas):
```powershell
.\mvnw.cmd test "-Dtest=CoveragePolicyTest,MembershipDatesTest,IsolationGuardTest"
```

Para ejecutar la suite completa de integración (requiere PostgreSQL configurado con esquema exclusivo de pruebas):
```powershell
$env:TEST_DB_SCHEMA="gymvip_test_jorge"
.\mvnw.cmd test
```

> [!IMPORTANT]
> `IsolationGuardTest` y la configuración de pruebas abortan automáticamente la ejecución si `TEST_DB_SCHEMA` apunta a `public`, está vacío o apunta al esquema de otro desarrollador, protegiendo los datos compartidos.

### 5.2 Iniciar el Backend Localmente
```powershell
.\mvnw.cmd spring-boot:run
```
El servidor quedará disponible en `http://localhost:8081`.

### 5.3 Usuarios de Prueba en Entorno de Desarrollo (`dev`)
Al iniciar con base de datos limpia, `DataInitializer` registra automáticamente:
- **Administrador:** `admin@vipcenterfit.pe` / Contraseña: valor de `APP_ADMIN_INITIAL_PASSWORD` (Rol: `ADMIN`)
- **Recepcionista:** `recepcion@vipcenterfit.pe` / Contraseña: valor de `APP_RECEPCION_INITIAL_PASSWORD` (Rol: `RECEPCION`)

---

## 6. Estado de Verificación y Pendientes

### 6.1 Implementado en Código en esta Rama
- ✅ **Base técnica y seguridad:** Spring Boot 3.4.3, Flyway V1–V5, Spring Security stateless.
- ✅ **HU-01, HU-04, HU-06 (André):** Registro de clientes, gestión de personal, catálogo de planes y versiones.
- ✅ **HU-07, HU-08, HU-10 (Alexander):** Contratación de membresías, cobros presenciales con constancia, idempotencia y renovaciones continuas.
- ✅ **HU-02, HU-09, HU-11, HU-12 (Jorge):** Edición de socios con auditoría campo por campo en `client_changes`, cálculo autoritativo de vigencias con zona Lima, listado de vencimientos y verificación manual en recepción.

### 6.2 Pruebas Ejecutadas en este Entorno
- **Compilación de código fuente:** **APROBADO** (86 clases fuente y 11 clases de pruebas compiladas con Java 21 / javac release 21 sin errores).
- **Pruebas unitarias de dominio ejecutadas:** **APROBADO** (9 pruebas ejecutadas en `IsolationGuardTest`, `CoveragePolicyTest` y `MembershipDatesTest`; 0 fallos, 0 errores, 0 omitidas).

### 6.3 Pruebas Reportadas en Documentos Técnicos Previos
- **Suite completa de Jorge (26/10/2026):** 58 pruebas automatizadas aprobadas contra PostgreSQL local portable (esquema `gymvip_test_jorge`), cubriendo auditoría, concurrencia optimista, consolidación de periodos pagados y control de acceso.
- **Suite de Alexander (26/10/2026):** 42 pruebas backend aprobadas en PostgreSQL local (esquema `gymvip_test_alexander`), validando transaccionalidad de cobros, renovaciones e idempotencia.
- **Suite de André (19/10/2026):** 23 pruebas en base de datos PostgreSQL 17.11 en Render (esquema `gymvip_test`).

### 6.4 Pendientes de Aceptación Formal
- **Ratificación de Reglas de Negocio:** Validación y aprobación por parte del Product Owner (Wagner) respecto a:
  - Convención horaria `America/Lima` con límites exactos de inicio incluido / fin excluido.
  - Ventana propuesta de 7 días (`MEMBERSHIP_UPCOMING_DAYS=7`) para el estado `POR_VENCER`.
  - Definición de medios de pago autorizados y políticas de cambio de plan en renovaciones.
- **Validación en Entorno Oficial Render:** Ejecución de la suite completa de 58 pruebas sobre la base de datos PostgreSQL 17.11 de Render cuando se suministren credenciales y un esquema exclusivo asignado para Jorge.
- **Revisión de Código e Integración:** Revisión cruzada de código (*code review*) entre compañeros y posterior integración formal a las ramas `develop` y `main` (sin merges prematuros en esta etapa).
- **Pruebas de Carga:** El listado de cobertura calcula bloques pagados en memoria antes de aplicar la paginación; se encuentra pendiente certificar su comportamiento ante volúmenes masivos de datos.

---

## 7. Documentación Técnica Canónica (Los 7 Markdown Oficiales)

Toda la documentación viva del proyecto se encuentra centralizada en la carpeta canónica [`../gymvip-frontend/documentos/`](../gymvip-frontend/documentos/):
1. [Reglas de Negocio, Alcance y DoD](../gymvip-frontend/documentos/REGLAS.md)
2. [Catálogo Oficial de Endpoints de API](../gymvip-frontend/documentos/APIDOCUMENTATION.md)
3. [Sistema de Diseño Visual y Figma](../gymvip-frontend/documentos/DESIGN.md)
4. [Ficha Técnica y Matriz de Infraestructura](../gymvip-frontend/documentos/FICHA_TECNICA.md)
5. [Arquitectura por Capas, ERD y Modelos de Ingeniería](../gymvip-frontend/documentos/ARQUITECTURA_Y_MODELOS.md)
6. [Matriz de Evidencias de QA y Seguridad](../gymvip-frontend/documentos/EVIDENCIAS_QA_SEGURIDAD.md)
7. [Estado del Proyecto y Trazabilidad de HU](../gymvip-frontend/documentos/ESTADO_PROYECTO.md)
- [Fuente de Diagramas C4 en PlantUML](../gymvip-frontend/documentos/c4.uml)
- [Archivos Históricos del Equipo](../gymvip-frontend/documentos/historico/)
