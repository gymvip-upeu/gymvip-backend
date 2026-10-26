# ESTADO_BASE_SPRINT1_ACOSTA.md â€” Estado TÃ©cnico de la Base Funcional (Sprint 1)

> **Proyecto:** GYM VIP (VIP CENTER FIT S.A.C.)
> **Responsable del Incremento:** Andre Acosta (Base ComÃºn + HU-01, HU-04, HU-06)
> **Fecha de ActualizaciÃ³n:** 19 de octubre de 2026
> **Rama Oficial de Entrega:** `feature/s1-andre-hu01-hu04-hu06` (descendiente del commit raÃ­z vacÃ­o de `main` y `develop`)
> **PatrÃ³n de Arquitectura:** Backend por Capas Generales (`controller`, `dto`, `entity`, `enums`, `exception`, `mapper`, `repository`, `security`, `service`, `util`)
> **Estado Global de la Base:** 100% OPERATIVA Y VERIFICADA EN SU ALCANCE (23/23 pruebas unitarias y de integraciÃ³n superadas sobre PostgreSQL 17.11 en Render)

---

## A. Alcance Delimitado: Primera Parte del Sprint 1

Este incremento tÃ©cnico corresponde **exclusivamente a la primera porciÃ³n del Sprint 1** asignada a Acosta. No representa la finalizaciÃ³n del Sprint 1 global del equipo ni agota el Product Backlog.

### Alcance Entregado:
1. **Infraestructura Base y Arquitectura por Capas:** ReorganizaciÃ³n completa del backend bajo el paquete raÃ­z `com.gymvip` en una arquitectura por capas generales y responsabilidades desacopladas, reemplazando la modularidad por dominios/contextos previa.
2. **Seguridad RBAC y Sesiones:** Filtro de autenticaciÃ³n stateless mediante token en cabecera HTTP (`Authorization: Bearer <token>` / `X-Auth-Token`), control de acceso por roles (`ADMIN`, `RECEPCION`) con respuestas 401 Unauthorized y 403 Forbidden estandarizadas, y almacenamiento de sesiones con caducidad.
3. **Manejo Centralizado de Excepciones:** `GlobalExceptionHandler` con mapeo seguro a cÃ³digos HTTP semÃ¡nticos (400, 401, 403, 404, 409, 500) y `ErrorResponse` estructurado con identificador de correlaciÃ³n (`correlationId`).
4. **HU-01 â€” Registrar Clientes (Alta de Socio):** Formulario web y endpoint `POST /api/v1/clients` con validaciÃ³n estricta de mayorÃ­a de edad (â‰¥ 18 aÃ±os), unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta HTTP 409 Conflict ante duplicados, validaciÃ³n estricta de DNI peruano (8 dÃ­gitos numÃ©ricos), datos de contacto opcionales (telÃ©fono y correo validados Ãºnicamente si se envÃ­an), normalizaciÃ³n (`trim` y minÃºsculas), generaciÃ³n de cÃ³digo de socio legible (`MemberCodeGenerator` ej. `MAR-8Q2L7K`) y endpoints de consulta (`GET /api/v1/clients`, `GET /api/v1/clients/{id}`, `GET /api/v1/clients/code/{code}`).
5. **HU-04 â€” Roles y GestiÃ³n de Personal:** MÃ³dulo de administraciÃ³n (`/api/v1/accounts`) con listado seguro sin exponer credenciales, creaciÃ³n con hash BCrypt seguro, asignaciÃ³n exclusiva de roles autorizados `ADMIN` o `RECEPCION` (purga completa de `SUPERADMIN` en controladores y consultas), inactivaciÃ³n de colaboradores con revocaciÃ³n inmediata de sesiones activas, y salvaguarda transaccional que impide inactivar o retirar permisos al Ãºltimo administrador activo del sistema (HTTP 409 Conflict).
6. **HU-06 â€” CatÃ¡logo de Planes:** GestiÃ³n completa de membresÃ­as comerciales (`/api/v1/plans`) con denominaciÃ³n normalizada Ãºnica, precio positivo en Soles `NUMERIC(12,2) PEN`, regla de negocio de duraciÃ³n mensual de **exactamente 30 dÃ­as**, regla de pase diario de **exactamente 1 dÃ­a**, versionado tarifario inmutable (`PlanVersion`) con consulta cronolÃ³gica en BD, control de concurrencia optimista (`@Version`) e inactivaciÃ³n lÃ³gica. Consulta pÃºblica/recepciÃ³n y mutaciones administrativas exclusivas.

### Fuera de Alcance de este Incremento:
- MembresÃ­as activas, asignaciÃ³n y contrataciÃ³n (HU-07: Alexander).
- Registro de cobros presenciales, mÃ©todos de pago y caja (HU-08: Alexander).
- RenovaciÃ³n periÃ³dica de membresÃ­as (HU-10: Alexander).
- BÃºsqueda avanzada y ediciÃ³n de ficha de socios (HU-02: Jorge).
- Regla de cÃ¡lculo de vigencias y estados VIGENTE/VENCIDO (HU-09: Jorge).
- Reporte operativo de vencimientos (HU-11: Jorge).
- VerificaciÃ³n manual de acceso en recepciÃ³n (HU-12: Jorge).
- Pasarela de pagos por internet, cobros automÃ¡ticos y mÃ³vil Flutter (Diferidos para Sprint 2 / E03).

---

## B. Estado Real por Historia de Usuario

| HU | Estado TÃ©cnico | Criterios Observados e Implementados | Pruebas Automatizadas | Pendientes / Limitaciones Abiertas |
| :--- | :--- | :--- | :--- | :--- |
| **HU-01: Registrar Clientes** | **APROBADO** | - Formulario web y API REST `POST /api/v1/clients`.<br>- ValidaciÃ³n estricta â‰¥ 18 aÃ±os (HTTP 400 ante menores).<br>- Unicidad documental `(PE, DNI, nÃºmero)` con HTTP 409 Conflict.<br>- DNI validado a exactamente 8 dÃ­gitos numÃ©ricos.<br>- TelÃ©fono y correo opcionales (validados en formato solo si se proveen).<br>- GeneraciÃ³n de cÃ³digo `MAR-XXXXXX` / `VIP-XXXXXX`.<br>- Consulta paginada `GET /api/v1/clients`. | `ClientIntegrationTest` (6 tests PASS)<br>- Registro exitoso con cÃ³digo socio.<br>- Bloqueo y 409 en documento repetido.<br>- Rechazo con 400 a menor de edad.<br>- Alta exitosa con telÃ©fono y correo omitidos.<br>- Rechazo con 400 a DNI de 7 dÃ­gitos o alfanumÃ©rico.<br>- Rechazo con 400 a formato de email invÃ¡lido. | - EdiciÃ³n y actualizaciÃ³n de datos de contacto reservada para HU-02 (Jorge).<br>- Soporte formal de menores con representante legal diferido. |
| **HU-04: Roles y Personal** | **APROBADO** | - API `/api/v1/accounts` protegida por rol `ADMIN`.<br>- Roles restringidos a `ADMIN` y `RECEPCION` (sin SUPERADMIN).<br>- Listado sin exponer `password_hash`.<br>- Hashing BCrypt con salt.<br>- InactivaciÃ³n revoca sesiones de `sessions`.<br>- ProtecciÃ³n activa del Ãºltimo administrador. | `AccountManagementIntegrationTest` (7 tests PASS)<br>- AutenticaciÃ³n y listado seguro.<br>- 403 Forbidden para rol RECEPCION.<br>- 401 para peticiÃ³n anÃ³nima.<br>- CreaciÃ³n de cajero y validaciÃ³n 409.<br>- Rechazo de roles no autorizados (400).<br>- Cambio de rol y estado activo/inactivo.<br>- Bloqueo ante inactivaciÃ³n del Ãºltimo admin. | - Interfaz de recuperaciÃ³n de contraseÃ±as por correo pendiente de servicio SMTP de producciÃ³n.<br>- Soporte MFA reservado para etapas posteriores. |
| **HU-06: CatÃ¡logo de Planes** | **APROBADO** | - DenominaciÃ³n Ãºnica case-insensitive (409 Conflict).<br>- Precio PEN positivo con `BigDecimal`.<br>- Regla inquebrantable de 30 dÃ­as para mensualidades.<br>- Regla inquebrantable de 1 dÃ­a exacto para pase diario.<br>- InstantÃ¡nea inmutable en tabla `plan_versions`.<br>- Concurrencia optimista mediante `@Version`.<br>- InactivaciÃ³n lÃ³gica sin borrado fÃ­sico. | `PlanIntegrationTest` (8 tests PASS)<br>- Admin crea plan y consulta en catÃ¡logo.<br>- RecepciÃ³n recibe 403 al intentar crear.<br>- Rechazo 400 por precio/duraciÃ³n negativos.<br>- Rechazo 400 en mensual con 31 dÃ­as.<br>- AprobaciÃ³n de mensual con 30 dÃ­as exactos.<br>- Pase diario de 1 dÃ­a aprobado y rechazo a 2 dÃ­as.<br>- Incremento de versiÃ³n (v1 -> v2) y comprobaciÃ³n en BD de conservaciÃ³n inmutable de ambas.<br>- RecepciÃ³n lista catÃ¡logo en modo lectura. | - Ninguna en el alcance de planes comerciales. La contrataciÃ³n efectiva corresponde a HU-07 (Alexander). |
| **Persistencia PostgreSQL** | **APROBADO Y VERIFICADO EN RENDER** | - Motor exclusivo PostgreSQL 17.11 en Render nube (`sslmode=require`).<br>- Dependencia H2 y perfiles de base embebida eliminados.<br>- Migraciones `V1__init_schema.sql` y `V2__add_member_code_to_clients.sql` aplicadas exitosamente por Flyway.<br>- Hibernate configurado con `ddl-auto=validate` y HikariCP optimizado (mÃ¡x 5 conexiones compartidas).<br>- Aislamiento de tests en esquema independiente `TEST_DB_SCHEMA=gymvip_test`. | Conectividad y suite completa ejecutadas contra PostgreSQL 17.11 en Render: **23/23 tests PASS (0 failures, 0 errors, 0 skipped)**.<br>Persistencia comprobada tras reinicio en backend y frontend. | Ninguna. ConexiÃ³n externa validada por TLS directo a hostname completo de Render sin exponer credenciales. |

---

## C. Estructura Final por Capas Generales

La organizaciÃ³n fÃ­sica del cÃ³digo fuente bajo `src/main/java/com/gymvip/` responde estrictamente al diseÃ±o por responsabilidades desacopladas:

```
src/main/java/com/gymvip/
â”œâ”€â”€ config/
â”‚   â”œâ”€â”€ AppConfig.java                 # Beans de soporte (PasswordEncoder BCrypt)
â”‚   â””â”€â”€ DataInitializer.java           # ProvisiÃ³n controlada en dev (admin, recepcion, 4 planes base)
â”œâ”€â”€ controller/
â”‚   â”œâ”€â”€ AccountController.java         # Endpoints /api/v1/accounts (ADMIN)
â”‚   â”œâ”€â”€ AuthController.java            # Endpoints /api/v1/auth (Login, Logout, Me)
â”‚   â”œâ”€â”€ ClientController.java          # Endpoints /api/v1/clients (ADMIN, RECEPCION)
â”‚   â””â”€â”€ PlanController.java            # Endpoints /api/v1/plans (PÃºblico lectura, ADMIN mutaciÃ³n)
â”œâ”€â”€ dto/
â”‚   â”œâ”€â”€ request/
â”‚   â”‚   â”œâ”€â”€ CreatePlanRequest.java
â”‚   â”‚   â”œâ”€â”€ CreateStaffAccountRequest.java
â”‚   â”‚   â”œâ”€â”€ LoginRequest.java
â”‚   â”‚   â”œâ”€â”€ RegisterClientRequest.java
â”‚   â”‚   â”œâ”€â”€ UpdatePlanRequest.java
â”‚   â”‚   â”œâ”€â”€ UpdatePlanStatusRequest.java
â”‚   â”‚   â”œâ”€â”€ UpdateStaffRoleRequest.java
â”‚   â”‚   â””â”€â”€ UpdateStaffStatusRequest.java
â”‚   â””â”€â”€ response/
â”‚       â”œâ”€â”€ AuthResponse.java
â”‚       â”œâ”€â”€ ClientDetailResponse.java
â”‚       â”œâ”€â”€ ClientResponse.java
â”‚       â”œâ”€â”€ ErrorResponse.java
â”‚       â”œâ”€â”€ PlanResponse.java
â”‚       â””â”€â”€ StaffAccountResponse.java
â”œâ”€â”€ entity/
â”‚   â”œâ”€â”€ Account.java                   # Entidad JPA tabla 'accounts'
â”‚   â”œâ”€â”€ AuditEvent.java                # Entidad JPA tabla 'audit_events'
â”‚   â”œâ”€â”€ Client.java                    # Entidad JPA tabla 'clients'
â”‚   â”œâ”€â”€ Document.java                  # Entidad JPA tabla 'documents'
â”‚   â”œâ”€â”€ Plan.java                      # Entidad JPA tabla 'plans'
â”‚   â”œâ”€â”€ PlanVersion.java               # Entidad JPA tabla 'plan_versions'
â”‚   â””â”€â”€ Session.java                   # Entidad JPA tabla 'sessions'
â”œâ”€â”€ enums/
â”‚   â”œâ”€â”€ AccountStatus.java             # ACTIVO, INACTIVO
â”‚   â”œâ”€â”€ ClientStatus.java              # ACTIVO, INACTIVO
â”‚   â”œâ”€â”€ PlanStatus.java                # ACTIVO, INACTIVO
â”‚   â””â”€â”€ Role.java                      # ADMIN, RECEPCION, CLIENTE
â”œâ”€â”€ exception/
â”‚   â”œâ”€â”€ AccountNotFoundException.java
â”‚   â”œâ”€â”€ ClientNotFoundException.java
â”‚   â”œâ”€â”€ ClientValidationException.java
â”‚   â”œâ”€â”€ ConflictException.java
â”‚   â”œâ”€â”€ DocumentDuplicateException.java
â”‚   â”œâ”€â”€ DuplicatePlanNameException.java
â”‚   â”œâ”€â”€ GlobalExceptionHandler.java    # @RestControllerAdvice centralizado (400, 401, 403, 404, 409, 500)
â”‚   â”œâ”€â”€ InvalidCredentialsException.java
â”‚   â”œâ”€â”€ InvalidPlanException.java
â”‚   â”œâ”€â”€ LastAdminProtectionException.java
â”‚   â”œâ”€â”€ PlanNotFoundException.java
â”‚   â””â”€â”€ ResourceNotFoundException.java
â”œâ”€â”€ mapper/
â”‚   â”œâ”€â”€ AccountMapper.java             # ConversiÃ³n Account -> StaffAccountResponse
â”‚   â”œâ”€â”€ ClientMapper.java              # ConversiÃ³n RegisterClientRequest -> Client -> Responses
â”‚   â””â”€â”€ PlanMapper.java                # ConversiÃ³n CreatePlanRequest -> Plan -> PlanResponse / PlanVersion
â”œâ”€â”€ repository/
â”‚   â”œâ”€â”€ AccountRepository.java         # Spring Data JPA + countActiveAdmins()
â”‚   â”œâ”€â”€ AuditEventRepository.java      # Spring Data JPA
â”‚   â”œâ”€â”€ ClientRepository.java          # Spring Data JPA + searchClients() paginado
â”‚   â”œâ”€â”€ DocumentRepository.java        # Spring Data JPA + existsByCountryCodeAndDocumentTypeAndDocumentNumber()
â”‚   â”œâ”€â”€ PlanRepository.java            # Spring Data JPA + existsByNameIgnoreCase()
â”‚   â”œâ”€â”€ PlanVersionRepository.java     # Spring Data JPA + findFirstByPlanIdOrderByVersionNumberDesc()
â”‚   â””â”€â”€ SessionRepository.java         # Spring Data JPA + deleteByAccountId()
â”œâ”€â”€ security/
â”‚   â”œâ”€â”€ SecurityConfig.java            # SecurityFilterChain, CORS, EntryPoint 401, AccessDeniedHandler 403
â”‚   â””â”€â”€ TokenAuthenticationFilter.java # Filtro Bearer / X-Auth-Token contra tabla sessions
â”œâ”€â”€ service/
â”‚   â”œâ”€â”€ AccountService.java            # Contrato de gestiÃ³n de personal y sesiones
â”‚   â”œâ”€â”€ AuditService.java              # Contrato de registro de auditorÃ­a
â”‚   â”œâ”€â”€ AuthService.java               # Contrato de autenticaciÃ³n y logout
â”‚   â”œâ”€â”€ ClientService.java             # Contrato de alta y consulta de socios
â”‚   â”œâ”€â”€ PlanService.java               # Contrato de planes y versionado
â”‚   â””â”€â”€ impl/
â”‚       â”œâ”€â”€ AccountServiceImpl.java    # ImplementaciÃ³n transaccional HU-04
â”‚       â”œâ”€â”€ AuditServiceImpl.java      # ImplementaciÃ³n transaccional auditorÃ­a
â”‚       â”œâ”€â”€ AuthServiceImpl.java       # ImplementaciÃ³n transaccional login/sesiÃ³n
â”‚       â”œâ”€â”€ ClientServiceImpl.java     # ImplementaciÃ³n transaccional HU-01
â”‚       â””â”€â”€ PlanServiceImpl.java       # ImplementaciÃ³n transaccional HU-06
â”œâ”€â”€ util/
â”‚   â””â”€â”€ MemberCodeGenerator.java       # Generador de cÃ³digos de socio (MAR-8Q2L7K)
â””â”€â”€ GymVipApplication.java             # Clase de arranque Spring Boot (@SpringBootApplication)
```

---

## D. GuÃ­a de Arranque y ConfiguraciÃ³n por Entorno

### D.1 Variables de Entorno del Backend (`.env.example`)
El backend carga `.env` automÃ¡ticamente mediante `spring.config.import=optional:file:./.env[.properties]` en `application.yml`:

```properties
SPRING_PROFILES_ACTIVE=dev
PORT=8081
DB_JDBC_URL=jdbc:postgresql://HOST_RENDER:5432/gymvip?sslmode=require
DB_USERNAME=gymvip_user
DB_PASSWORD=TU_PASSWORD_AQUI
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
TEST_DB_SCHEMA=gymvip_test
```

### D.2 ConfiguraciÃ³n para Render (Desarrollo Compartido / ProducciÃ³n)
```properties
SPRING_PROFILES_ACTIVE=dev
DB_JDBC_URL=jdbc:postgresql://dpg-dbb4q767bikc73d2uop0-a.oregon-postgres.render.com:5432/gymvip?sslmode=require
DB_USERNAME=gymvip_user
DB_PASSWORD=TU_PASSWORD_PRIVADO_EN_ENV
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
TEST_DB_SCHEMA=gymvip_test
```
- Perfil `dev` y `prod` activan `spring.jpa.hibernate.ddl-auto=validate`.
- Flyway aplica automÃ¡ticamente las migraciones pendientes sobre la BD de Render (`V1` y `V2`).
- Las pruebas de Maven se ejecutan de forma aislada en el esquema `gymvip_test` (`TEST_DB_SCHEMA=gymvip_test`), sin colisionar ni alterar las tablas compartidas en `public`.

### D.3 Variables de Entorno del Frontend (`gymvip-frontend/.env`)
```properties
VITE_API_BASE_URL=
```
- Al dejarlo vacÃ­o, Vite utiliza el proxy configurado en `vite.config.js` redirigiendo `/api/v1` hacia `http://localhost:8081` de forma transparente.

### D.4 Comandos de EjecuciÃ³n Local
```powershell
# 1. Ejecutar pruebas unitarias y de integraciÃ³n del backend (PostgreSQL Render aislado):
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

## E. Evidencia MÃ­nima de la IntervenciÃ³n TÃ©cnica

### E.1 Pruebas Automatizadas Ejecutadas
- **Comando:** `.\mvnw.cmd test`
- **Resultados:** `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` (100% PASS en PostgreSQL 17.11 Render).
- **Desglose de suites:**
  - `DatabaseConnectivityCheckTest`: 1 prueba (ComprobaciÃ³n de motor PostgreSQL 17.11 en Render, catÃ¡logo `gymvip`, esquema Flyway aislado `gymvip_test`).
  - `GymVipApplicationTests`: 1 prueba (Arranque de contexto Spring Boot con PostgreSQL).
  - `AccountManagementIntegrationTest`: 7 pruebas (Listado, 401, 403, alta personal, duplicados, cambio rol/status, protecciÃ³n Ãºltimo admin).
  - `ClientIntegrationTest`: 6 pruebas (Alta socio con cÃ³digo `MAR-XXXXXX` / `JOA-XXXXXX`, unicidad documental 409, bloqueo menor de edad 400, alta con campos opcionales omitidos, bloqueo de DNI invÃ¡lido de 7 dÃ­gitos/alfanumÃ©rico, bloqueo de correo invÃ¡lido).
  - `PlanIntegrationTest`: 8 pruebas (Alta de plan, 403 recepciÃ³n, validaciÃ³n valores negativos, regla mensual 30 dÃ­as, pase diario de 1 dÃ­a aprobado y rechazo a 2 dÃ­as, incremento de versiÃ³n v1->v2 con comprobaciÃ³n en BD de conservaciÃ³n inmutable de ambas, inactivaciÃ³n, listado por recepciÃ³n).

### E.2 CompilaciÃ³n del Frontend
- **Comando:** `npm run build` en `gymvip-frontend`.
- **Resultado:** `vite build` completado exitosamente en 6.02s sin errores ni warnings.

### E.3 Archivos Retirados y Reubicados
- `gymvip-backend/scripts/export_issues.py`: Retirado del repositorio de cÃ³digo backend y preservado con valor documental en `documentos/scripts/export_issues.py`.
- `gymvip-backend/ESTADO_BACKEND_BASE.md`: Retirado de la raÃ­z del backend tras consolidaciÃ³n.
- `gymvip-backend/docs/*`: Retirados todos los documentos dispersos y redundantes (`C4_BASE_ACTUAL.md`, `COMMIT_LOG_BASE.md`, `ERD_BASE_ACTUAL.md`, `ESTADO_BACKEND_BASE.md`, `HANDOFF_BASE_SPRINT1.md`, `MODELOS_BASE_ACTUAL.md`, subcarpetas `arquitectura` y `producto`), integrando su contenido en `documentos/` canÃ³nico.
- `gymvip-backend/data/`: Excluido en `.gitignore` tras confirmar que correspondÃ­a a persistencia local anterior H2; la suite de pruebas ahora opera en memoria sin generar archivos de BD.

---

## F. Base TÃ©cnica que Reciben los CompaÃ±eros

Alexander y Jorge reciben un nÃºcleo robusto, tipado y probado que deben reutilizar directamente:

1. **Entidades JPA Disponibles (`com.gymvip.entity`):**
   - `Client`: Socio del gimnasio con `memberCode` Ãºnico, nombres, fecha de nacimiento, datos de contacto y colecciÃ³n de documentos.
   - `Document`: Documento de identidad normalizado con restricciÃ³n de unicidad compuesta `(countryCode, documentType, documentNumber)`.
   - `Account`: Cuenta institucional con roles (`ADMIN`, `RECEPCION`) y contraseÃ±a encriptada.
   - `Session`: SesiÃ³n activa con token de 128 caracteres y fecha de expiraciÃ³n.
   - `Plan`: Plan comercial de membresÃ­a con precio en PEN, duraciÃ³n en dÃ­as y control de versiÃ³n optimista.
   - `PlanVersion`: InstantÃ¡nea histÃ³rica inmutable de tarifas y condiciones contractuales.
   - `AuditEvent`: BitÃ¡cora inmutable de eventos del sistema (actor, acciÃ³n, entidad, detalles, timestamp).

2. **Servicios y Repositorios Reutilizables:**
   - InyecciÃ³n de `ClientRepository`, `DocumentRepository`, `PlanRepository`, `PlanVersionRepository`, `AccountRepository`, `SessionRepository`, `AuditEventRepository`.
   - `AuditService.recordEvent(...)`: Reutilizable para registrar cualquier mutaciÃ³n econÃ³mica o de socio.
   - `GlobalExceptionHandler`: Captura automÃ¡ticamente `IllegalArgumentException`, `MethodArgumentNotValidException`, `ConflictException`, etc.

3. **CoordinaciÃ³n de Migraciones Flyway:**
   - `V1__init_schema.sql` y `V2__add_member_code_to_clients.sql` son inmutables.
   - La siguiente migraciÃ³n debe nombrarse obligatoriamente: **`V3__<descripcion_breve>.sql`**.

---

## G. Tareas Asignadas a Alexander y Jorge (Sprint 1)

> [!IMPORTANT]
> **Reparto Nominal Propuesto para el Sprint 1:**
> Basado en la delimitaciÃ³n de las partes 2 y 3 del incremento. Debe ratificarse formalmente en la reuniÃ³n del equipo.

### Alexander â€” Responsable Parte 2: HU-07, HU-08, HU-10

- **HU-07 â€” AsignaciÃ³n y ContrataciÃ³n de Plan:**
  - Crear entidad `Membership` en `entity/`, `MembershipRepository` en `repository/`, `MembershipService` en `service/` y `MembershipController` en `controller/`.
  - Vincular al socio (`Client`) con la instantÃ¡nea inmutable del plan (`PlanVersion`).
  - La contrataciÃ³n de plan y el asiento de cobro inicial deben ocurrir dentro de una **misma transacciÃ³n atÃ³mica** (`@Transactional`) con reversiÃ³n completa ante fallos.
  - Soportar clave de idempotencia (`Idempotency-Key` en cabecera HTTP) para evitar cobros o contrataciones duplicadas ante reintentos de red.
- **HU-08 â€” Registro de Pagos Presenciales:**
  - Crear entidad `PaymentRecord` en `entity/`, vinculada a `Membership` y `Account` (recepcionista que cobra).
  - MÃ©todos de pago vÃ¡lidos: `EFECTIVO`, `TARJETA`, `YAPE_PLIN`.
  - Guardar comprobante/referencia externa opcional y registrar evento en `AuditService`.
- **HU-10 â€” RenovaciÃ³n de MembresÃ­as:**
  - Consumir la polÃ­tica de cÃ¡lculo de fechas que provee Jorge.
  - RenovaciÃ³n de 30 dÃ­as exactos: si el socio renueva antes de vencer, el nuevo perÃ­odo se acumula a partir del fin del perÃ­odo vigente; si renueva despuÃ©s de vencer, el nuevo perÃ­odo inicia desde la fecha del pago.
- **Frontend:**
  - Integrar vistas `MembershipsView.jsx` y `PaymentsView.jsx` consumiendo el `Sidebar.jsx`, `apiClient.js` y el diseÃ±o Dark & Gold existente.
- **Modelado UML:**
  - Mantener a su cargo los diagramas de clases, paquetes y actividades del negocio del equipo.

---

### Jorge â€” Responsable Parte 3: HU-02, HU-09, HU-11, HU-12 (Corte Manual)

- **HU-02 â€” BÃºsqueda y EdiciÃ³n de Socios:**
  - Extender `ClientService` y `ClientController` con endpoints de actualizaciÃ³n de datos de contacto (`phone`, `email`).
  - Prohibir la alteraciÃ³n arbitraria de nÃºmero de documento sin procedimiento formal de rectificaciÃ³n.
  - AÃ±adir filtros avanzados en `ClientRepository.searchClients(...)`.
- **HU-09 â€” CÃ¡lculo y Consulta de Vigencia:**
  - Implementar servicio puro de cÃ¡lculo de vigencias sin depender de `plusMonths` (mes = exactamente 30 dÃ­as, inicio inclusivo, fin exclusivo a las 23:59:59).
  - Estados calculados: `VIGENTE`, `PROXIMO_A_VENCER` (â‰¤ 5 dÃ­as para expirar), `VENCIDO`.
  - Proveer este componente de fechas como dependencia limpia para que Alexander lo consuma en la contrataciÃ³n y renovaciÃ³n.
- **HU-11 â€” Reporte Operativo de Vencimientos:**
  - Endpoint de consulta para recepciÃ³n que devuelva los socios con membresÃ­as por expirar en los prÃ³ximos 7 dÃ­as para gestiÃ³n preventiva.
- **HU-12 â€” VerificaciÃ³n Manual de Acceso en RecepciÃ³n:**
  - Pantalla Ã¡gil en el frontend para el personal de recepciÃ³n: digita o busca el cÃ³digo de socio / documento y el sistema muestra un indicador visual en tiempo real:
    - ðŸŸ¢ **ACCESO PERMITIDO (VERDE):** Socio con membresÃ­a activa y vigente.
    - ðŸ”´ **ACCESO DENEGADO (ROJO):** Socio con membresÃ­a vencida, inactiva o inexistente.
  - Este alcance es manual operativo de recepciÃ³n; no incluye integraciÃ³n con torniquetes biomÃ©tricos ni lectores QR mÃ³viles.
- **Frontend:**
  - Construir vista de ediciÃ³n de socios, reporte de vencimientos y pantalla de control de acceso en recepciÃ³n utilizando el diseÃ±o Luxury Dark & Gold.

---

## H. Instrucciones para los Agentes de Alexander y Jorge (Protocolo de Relevo)

Para garantizar la integridad y coherencia del proyecto durante la incorporaciÃ³n de las partes 2 y 3, los agentes que asuman los roles de Alexander y Jorge deben cumplir estrictamente el siguiente protocolo:

a) **Verificar publicaciÃ³n y commits base:** Verificar que la rama `origin/feature/s1-andre-hu01-hu04-hu06` estÃ© publicada en GitHub y comprobar la pareja de commits entregada por AndrÃ© en backend y frontend.
b) **CreaciÃ³n de ramas individuales:** Crear su rama de trabajo individual exclusivamente desde `origin/feature/s1-andre-hu01-hu04-hu06`:
   - Alexander: `feature/s1-alexander-hu07-hu08-hu10`
   - Jorge: `feature/s1-jorge-hu02-hu09-hu11-hu12`
   *NUNCA crear su rama desde `develop` ni `main`, ya que ambas ramas comienzan con un Ã¡rbol vacÃ­o y no contienen el cÃ³digo base.*
c) **Conservar cÃ³digo base heredado:** Preservar la arquitectura por capas bajo `com.gymvip`, los controladores, DTOs, entidades y utilidades existentes sin reescribir ni renombrar paquetes base.
d) **Implementar Ãºnicamente su alcance autorizado:** Limitarse estrictamente a las HU asignadas para el Sprint 1 (Alexander: HU-07, HU-08, HU-10; Jorge: HU-02, HU-09, HU-11, HU-12 manual).
e) **ValidaciÃ³n y pruebas pertinentes:** Probar individualmente cada CA, DoD y DoR con la base PostgreSQL de desarrollo (`public` para pruebas manuales, esquema aislado `gymvip_test` para suites de Maven) y comprobar la persistencia tras reinicio.
f) **ActualizaciÃ³n documental y commits coherentes:** Actualizar la documentaciÃ³n de estado (`docs/`) y crear commits atÃ³micos por funcionalidad usando rutas explÃ­citas tras revisar `git diff`.
g) **Push con upstream de su propia rama:** Publicar su rama individual mediante `git push --set-upstream origin <nombre-de-rama>` en los repositorios afectados.
h) **ComprobaciÃ³n de sincronizaciÃ³n remota:** Verificar mediante `git ls-remote --heads origin` que el hash remoto coincide con el commit local final de su entrega.
i) **Entrega de enlaces reales:** Registrar en su informe los enlaces exactos de ramas y commits creados en GitHub.
j) **ProhibiciÃ³n de fusiÃ³n prematura:** **NO fusionar todavÃ­a a `develop` ni `main`**, ni borrar ramas de compaÃ±eros. La unificaciÃ³n de ramas se realizarÃ¡ en la sesiÃ³n de integraciÃ³n coordinada por el equipo.


## Continuidad de Alexander — 26/10/2026

HU-07/08/10 añadidas en su rama individual. Ver [estado actual](ESTADO_ALEXANDER_SPRINT1.md) y [matriz](MATRIZ_ALEXANDER_S1.md). Esta actualización no atribuye a André ni a Wagner aceptación del incremento nuevo.
