# Estado de la Base Funcional Backend — GYM VIP
**Responsable:** André Acosta  
**Fecha:** 14 de Octubre de 2026  
**Rama Git:** `base-acosta`  
**Último Commit Base:** `2bc1261` (Previo a commits de Clean Architecture)  
**Stack Técnico:** Java 21 LTS (compilado en Java 24 host en compatibilidad release 21) | Spring Boot 3.4.3 | Spring Security 6 | Flyway | Maven Wrapper  
**Persistencia:** PostgreSQL compatible / H2 dev persistente / Render PostgreSQL provisionado mediante variables de entorno  
**Estado de Despliegue Render:** PREPARADO (.env y variables JDBC configuradas) — **Render NO VERIFICADO** (se requiere instancia activa y credenciales privadas del proveedor).  

---

## 1. Criterios de Aceptación por Historia de Usuario (DoD)

### HU-01: Registro de Clientes
- [x] **IMPLEMENTADO OBSERVADO:** Alta de socio adulto (>= 18 años) con nombres, apellidos, tipo/número de documento, teléfono y correo opcional.
- [x] **VERIFICADO:** Unicidad documental compuesta (`type, number`) forzada en PostgreSQL con respuesta HTTP `409 Conflict` (`DocumentDuplicateException`).
- [x] **VERIFICADO:** Generación determinista y segura de código de socio legible con prefijo y sufijo aleatorio (`MAR-8Q2L7K`) mediante `MemberCodeGenerator` con bucle de reintento ante colisión y restricción única en BD.
- [x] **IMPLEMENTADO OBSERVADO:** Lectura mínima paginada de clientes (`GET /api/v1/clients`) y consulta individual de ficha (`GET /api/v1/clients/{id}`).
- [x] **IMPLEMENTADO OBSERVADO:** Eliminación de casillas de términos y cuadros de consentimiento ficticios en la solicitud (`RegisterClientRequest`).
- [ ] **FUERA DE ESTA BASE (Pendiente para Compañero en HU-02):** Edición de clientes, desactivación lógica, filtros avanzados e historial médico/deportivo.

### HU-04: Personal y Roles Institucionales
- [x] **IMPLEMENTADO OBSERVADO:** Cuentas institucionales con roles `ADMIN` y `RECEPCION` (`Account`, `AccountJpaEntity`).
- [x] **VERIFICADO:** Hash seguro de contraseñas con `BCryptPasswordEncoder` (con salt). No se exponen hashes en respuestas ni auditoría.
- [x] **VERIFICADO:** Seguridad y RBAC a nivel de endpoint mediante `@PreAuthorize("hasRole('ADMIN')")` y `@PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN')")`.
- [x] **VERIFICADO:** Control de accesos estricto: Peticiones anónimas devuelven **HTTP 401 Unauthorized**; roles insuficientes devuelven **HTTP 403 Forbidden**.
- [x] **VERIFICADO:** Revocación inmediata de todas las sesiones activas en base de datos al cambiar de rol o inactivar una cuenta.
- [x] **VERIFICADO:** Salvaguarda activa contra la democión o inactivación del último administrador (`LastAdminProtectionException` -> HTTP 400 Bad Request).
- [x] **IMPLEMENTADO OBSERVADO:** Provisión inicial de administrador configurable mediante variables privadas (`APP_ADMIN_INITIAL_PASSWORD`).

### HU-06: Catálogo de Planes y Versiones Tarifarias
- [x] **IMPLEMENTADO OBSERVADO:** Catálogo de planes con nombre normalizado único, duración en días, precio en PEN (`BigDecimal` / `NUMERIC(12,2)`), beneficios y estado (`ACTIVO` / `INACTIVO`).
- [x] **VERIFICADO:** Regla de negocio de gimnasio: los planes con denominación mensual deben tener una duración estándar de exactamente **30 días** (`InvalidPlanException` -> HTTP 400).
- [x] **VERIFICADO:** Versionado inmutable: Cada actualización de condiciones económicas incrementa la versión del plan (`version = version + 1`) y genera un registro congelado en `plan_versions` (`PlanVersion.snapshot`).
- [x] **VERIFICADO:** Control de concurrencia optimista mediante `@Version` de JPA en `PlanJpaEntity` y actualización atómica en `PlanPersistenceAdapter`.
- [x] **VERIFICADO:** RECEPCION puede consultar el catálogo de planes (`GET /api/v1/plans`), pero cualquier intento de crear o modificar devuelve HTTP 403 Forbidden.
- [ ] **FUERA DE ESTA BASE (Pendiente para Compañero en HU de Membresías):** Asignación de planes a socios, cobros, pasarelas de pago y renovaciones.

---

## 2. Organización Clean Architecture Observada en Backend

Estructura modular por contexto delimitado en el paquete raíz `com.gymvip`:

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

---

## 3. Verificaciones Automatizadas Ejecutadas Realmente

Comando ejecutado:
`.\mvnw test`

Resultados observados en la suite de integración Spring Boot:
- **`com.gymvip.GymVipApplicationTests`**: Context Loads [PASS - 1/1]
- **`com.gymvip.clients.ClientIntegrationTest`**:
  - `registerAdultClientSuccess` (Genera código socio MAR-XXXXXX, valida 409 en duplicado) [PASS]
  - `registerMinorClientBlocked` (Bloquea menor de edad con HTTP 400) [PASS]
- **`com.gymvip.identity.AccountManagementIntegrationTest`**:
  - `adminCanListAccounts` [PASS]
  - `recepcionForbiddenFromAccounts` (Valida HTTP 403) [PASS]
  - `adminCanCreateNewStaffMember` [PASS]
  - `adminCanUpdateStaffRole` [PASS]
  - `cannotDemoteLastAdmin` (Valida HTTP 400 salvaguarda) [PASS]
  - `cannotInactivateLastAdmin` (Valida HTTP 400 salvaguarda) [PASS]
  - `inactivationRevokesActiveSessions` [PASS]
- **`com.gymvip.plans.PlanIntegrationTest`**:
  - `adminCanCreateAndListPlan` [PASS]
  - `recepcionCannotCreatePlan` (Valida HTTP 403) [PASS]
  - `invalidPlanValuesReturnBadRequest` [PASS]
  - `monthlyPlanMustHaveExactly30Days` (Valida regla 30 días) [PASS]
  - `updatePlanIncrementsVersionAndInactivates` (Valida versión 2 y snapshot) [PASS]
  - `recepcionCanListPlans` [PASS]

**Total de Pruebas:** 16 ejecutadas, 0 fallos, 0 errores (100% APROBADO).
