> **Incremento Alexander S1 — 26/10/2026:** HU-07/08/10 implementadas en `feature/s1-alexander-hu07-hu08-hu10`. Contratación/cobro/renovación conectadas con API y PostgreSQL real; 42 pruebas backend y build frontend correctos. Validación local PostgreSQL 18.4; Render 17, acuerdos con Jorge/Wagner y aceptación formal pendientes. Ver [estado](docs/ESTADO_ALEXANDER_SPRINT1.md) y [matriz de criterios](docs/MATRIZ_ALEXANDER_S1.md). La descripción de la entrega original de André se conserva debajo como antecedente.

# GYM VIP â€” Backend API (Base Funcional Sprint 1 â€” Acosta)

> **Repositorio Oficial:** [gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend.git)
> **OrganizaciÃ³n:** `gymvip-upeu`
> **Responsable del Incremento:** Andre Acosta (Base comÃºn + HU-01, HU-04, HU-06)
> **Rama Publicada de Entrega:** `feature/s1-andre-hu01-hu04-hu06`
> **Stack Base:** Java 21 LTS (objetivo POM; comprobado en JDK 24.0.1 host), Spring Boot 3.4.3, Spring Data JPA, Spring Security 6, Flyway 10, PostgreSQL 17.11 exclusivo en Render (sin H2), Maven Wrapper 3.9.10.

---

## 1. PolÃ­tica de Ramas y Acceso al CÃ³digo

> [!IMPORTANT]
> **IMPORTANTE â€” CÃ³mo obtener la aplicaciÃ³n:**
> Las ramas `main` y `develop` han sido inicializadas con un **Ã¡rbol de archivos vacÃ­o** de acuerdo con la estrategia de integraciÃ³n diferida del equipo. Clonar `main` o `develop` no entrega el cÃ³digo funcional en esta etapa.
> Para descargar y trabajar sobre la base completa entregada por AndrÃ©:
> ```bash
> git clone https://github.com/gymvip-upeu/gymvip-backend.git
> cd gymvip-backend
> git checkout feature/s1-andre-hu01-hu04-hu06
> ```

- **`main`**: Contiene el commit inicial con Ã¡rbol vacÃ­o. Reservada para despliegues de producciÃ³n unificados.
- **`develop`**: Apunta inicialmente al commit vacÃ­o de `main`. Reservada para la integraciÃ³n posterior del equipo.
- **`feature/s1-andre-hu01-hu04-hu06`**: Rama oficial con la base tÃ©cnica, configuraciÃ³n, pruebas y las HU-01, HU-04 y HU-06.
- **Ramas de Alexander y Jorge**: DeberÃ¡n crearse a partir de `origin/feature/s1-andre-hu01-hu04-hu06` (nunca desde `develop` mientras estÃ© vacÃ­a).

---

## 2. Alcance Activo y DelimitaciÃ³n

Este repositorio contiene la **base funcional por capas generales** y las tres Historias de Usuario asignadas a Acosta en el Sprint 1:

1. **Base ComÃºn:**
   - Arquitectura por capas desacopladas (`controller`, `dto`, `entity`, `enums`, `exception`, `mapper`, `repository`, `security`, `service`, `util`). *Nota metodolÃ³gica: No se certifica "Clean Architecture" pura por la sola nomenclatura de carpetas; se trata de una arquitectura en capas desacopladas con servicios transaccionales.*
   - AutenticaciÃ³n y seguridad RBAC stateless por token en cabecera HTTP (`Authorization: Bearer <token>` o `X-Auth-Token`) con respuestas 401 Unauthorized y 403 Forbidden estandarizadas.
   - Carga explÃ­cita del `.env` mediante `spring.config.import=optional:file:./.env[.properties]`.
   - Motor exclusivo **PostgreSQL 17.11 en Render**. EliminaciÃ³n completa de `com.h2database:h2`, dialectos y URLs `jdbc:h2`.
   - Flyway como gestor exclusivo de migraciones (`V1` y `V2`).
   - Hibernate configurado con `ddl-auto: validate` (sin mutaciÃ³n destructiva ni automÃ¡tica de tablas).
   - Manejo centralizado de excepciones con `GlobalExceptionHandler` (400, 401, 403, 404, 409, 500) y `ErrorResponse` tipado.
2. **HU-01 â€” Registrar Clientes (Alta de Socio):**
   - Endpoints: `POST /api/v1/clients`, `GET /api/v1/clients` (paginado y bÃºsqueda), `GET /api/v1/clients/{id}`, `GET /api/v1/clients/code/{code}`.
   - ValidaciÃ³n estricta de mayorÃ­a de edad (â‰¥ 18 aÃ±os).
   - Unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta HTTP 409 Conflict ante duplicados.
   - DNI peruano validado a exactamente 8 dÃ­gitos numÃ©ricos.
   - TelÃ©fono y correo opcionales (validados en formato solo si se envÃ­an).
   - GeneraciÃ³n determinista de cÃ³digo de socio legible (`MemberCodeGenerator`, ej. `MAR-8Q2L7K`, `JOA-W38DM6`).
3. **HU-04 â€” Roles y Permisos (GestiÃ³n de Personal):**
   - Endpoints: `GET /api/v1/accounts`, `POST /api/v1/accounts`, `PATCH /api/v1/accounts/{id}/role`, `PATCH /api/v1/accounts/{id}/status`. Protegidos exclusivamente para rol `ADMIN` (403 Forbidden a `RECEPCION`).
   - Roles restringidos estrictamente a `ADMIN` y `RECEPCION` (sin SUPERADMIN en controladores ni consultas de negocio).
   - Listado seguro sin exponer hashes de contraseÃ±as. Hashing con BCrypt y salt.
   - InactivaciÃ³n de colaborador con revocaciÃ³n inmediata de sesiones activas.
   - Salvaguarda transaccional del Ãºltimo administrador activo (HTTP 409 Conflict).
4. **HU-06 â€” CatÃ¡logo de Planes:**
   - Endpoints: `GET /api/v1/plans` (pÃºblico / recepciÃ³n), `POST /api/v1/plans` (ADMIN), `PUT /api/v1/plans/{id}` (ADMIN), `PATCH /api/v1/plans/{id}/status` (ADMIN).
   - DenominaciÃ³n Ãºnica case-insensitive (409 Conflict ante duplicados).
   - Precios positivos en Soles `NUMERIC(12,2) PEN`.
   - Regla inquebrantable de duraciÃ³n mensual de **exactamente 30 dÃ­as** (31 dÃ­as rechazado con HTTP 400).
   - Regla de pase diario de **exactamente 1 dÃ­a** (2 dÃ­as rechazado con HTTP 400).
   - Versionado tarifario inmutable (`PlanVersion` en tabla `plan_versions`) que congela las condiciones de contrataciones previas.
   - Concurrencia optimista (`@Version`) e inactivaciÃ³n lÃ³gica.

> [!IMPORTANT]
> **DelimitaciÃ³n de Alcance â€” Fuera de este Incremento:**
> - **Alexander (Parte 2):** AsignaciÃ³n y contrataciÃ³n de membresÃ­as (HU-07), registro de cobros presenciales en efectivo/tarjeta (HU-08), renovaciÃ³n periÃ³dica de 30 dÃ­as (HU-10).
> - **Jorge (Parte 3):** BÃºsqueda avanzada y ediciÃ³n de datos de contacto de socios (HU-02), cÃ¡lculo autoritativo de vigencias VIGENTE/POR_VENCER/VENCIDO (HU-09), reporte de prÃ³ximos a vencer (HU-11), verificaciÃ³n manual de acceso en recepciÃ³n (HU-12).
> - Para el detalle exhaustivo, consulte [docs/ESTADO_BASE_SPRINT1_ACOSTA.md](docs/ESTADO_BASE_SPRINT1_ACOSTA.md).

---

## 3. Matriz de Versiones y Stack Real

| Componente | VersiÃ³n Configurada / Objetivo | VersiÃ³n Real en Host / Entorno | Observaciones |
| :--- | :--- | :--- | :--- |
| **Java JDK** | Java 21 LTS (`pom.xml`) | Java 24.0.1 (Oracle OpenJDK) | El POM fija `java.version=21` para portabilidad; Java 24 compila y ejecuta con soporte completo. |
| **Spring Boot** | 3.4.3 | 3.4.3 | Starter web, data-jpa, security, validation. |
| **Maven** | 3.9.10 (Maven Wrapper) | 3.9.10 | Ejecutar siempre mediante `.\mvnw.cmd` o `./mvnw`. |
| **Motor Base de Datos** | PostgreSQL 17.x | PostgreSQL 17.11 (Debian en Render) | ConexiÃ³n SSL (`sslmode=require`) al host externo de Render. |
| **Flyway** | 10.20.1 | 10.20.1 | MÃ³dulo `flyway-database-postgresql` integrado. |
| **HikariCP** | 5.1.0 | 5.1.0 | Pool acotado: `maximum-pool-size=5`, `minimum-idle=1`. |

---

## 4. Base de Datos, Esquemas y Variables de Entorno

### 4.1 Carga ExplÃ­cita de `.env`
El archivo `.env` se importa automÃ¡ticamente desde la raÃ­z del backend:
```yaml
spring:
  config:
    import: "optional:file:./.env[.properties]"
```

### 4.2 Plantilla `.env.example`
```properties
SPRING_PROFILES_ACTIVE=dev
PORT=8081
DB_JDBC_URL=jdbc:postgresql://dpg-dbb4q767bikc73d2uop0-a.oregon-postgres.render.com:5432/gymvip?sslmode=require
DB_USERNAME=gymvip_user
DB_PASSWORD=CAMBIAR_POR_TU_PASSWORD_RENDER
TEST_DB_SCHEMA=gymvip_test
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
```

### 4.3 SeparaciÃ³n de Esquemas: Desarrollo vs Pruebas
- **Esquema Compartido (`public`):** Utilizado en modo desarrollo (`spring-boot:run`). Comparte los datos de clientes, planes y cuentas de personal creados interactivamente por el equipo.
- **Esquema Aislado de Pruebas (`gymvip_test`):** Configurado en `src/test/resources/application.yml` mediante `TEST_DB_SCHEMA=gymvip_test`. Flyway crea y migra las tablas de prueba en este esquema; Hibernate valida contra Ã©l. **NingÃºn test automatizado limpia ni modifica el esquema `public`**.
- **Idempotencia:** `DataInitializer` verifica la existencia de registros antes de insertar las cuentas base `admin@vipcenterfit.pe` y `recepcion@vipcenterfit.pe`, garantizando que reinicios sucesivos no dupliquen datos ni alteren contraseÃ±as.

### 4.4 Migraciones Flyway
- `V1__init_schema.sql`: Estructura inicial de cuentas, roles, sesiones, clientes, documentos, planes, plan_versions y auditorÃ­a.
- `V2__add_member_code_to_clients.sql`: Agrega columna `member_code VARCHAR(30)` con constraint de unicidad.
- *PrÃ³xima migraciÃ³n para Alexander / Jorge:* **`V3__<descripcion>.sql`**.

---

## 5. Instrucciones de CompilaciÃ³n y EjecuciÃ³n

### 5.1 Ejecutar Suite de Pruebas en PostgreSQL Render
```powershell
.\mvnw.cmd test
```
- **Resultado Ejecutado:** **23/23 tests PASS (0 failures, 0 errors, 0 skipped)** en esquema `gymvip_test` sobre PostgreSQL 17.11 en Render.

### 5.2 Iniciar el Backend Localmente
```powershell
.\mvnw.cmd spring-boot:run
```
- El servidor arrancarÃ¡ en **`http://localhost:8081`** (configurado en `PORT=8081` para evitar colisiÃ³n con el puerto 8080 local).

### 5.3 Cuentas Iniciales para Pruebas en Desarrollo (`dev`)
- **Administrador:** `admin@vipcenterfit.pe` / `Admin123!` (Rol: `ADMIN`)
- **RecepciÃ³n:** `recepcion@vipcenterfit.pe` / `Recepcion123!` (Rol: `RECEPCION`)

---

## 6. Estado de VerificaciÃ³n y Pendientes

- **Pruebas Automatizadas:** **APROBADO** (23/23 tests PASS en PostgreSQL real).
- **Persistencia tras Reinicio:** **APROBADO** (Comprobada relectura de socio `Valeria Mendoza Quiroz` y versionado de `Plan Funcional Render 7520` tras apagar y reanudar el backend).
- **RevisiÃ³n de Wagner:** **PENDIENTE** (Pendiente de sesiÃ³n formal de evaluaciÃ³n y firma de aceptaciÃ³n).

Consulte la bitÃ¡cora tÃ©cnica detallada en:
ðŸ‘‰ **[docs/ESTADO_BASE_SPRINT1_ACOSTA.md](docs/ESTADO_BASE_SPRINT1_ACOSTA.md)**
