# ESTADO_BACKEND_BASE.md — Informe de Estado y Validación (Backend)

> **Proyecto:** GYM VIP (VIP CENTER FIT)  
> **Componente:** Backend API Spring Boot  
> **Responsable:** Andre Acosta  
> **Fecha de Inspección y Cierre:** 13 de Octubre de 2026  
> **Rama de Trabajo:** `base-acosta`  
> **Remoto:** `https://github.com/gymvip-upeu/gymvip-backend.git`

---

## 1. Resumen Ejecutivo de la Inspección

Se ejecutó la inspección exhaustiva de la línea base del backend, identificando dependencias activas, estado de la base de datos y alcance real frente a lo reportado en sprints previos.  
Se aplicó un recorte estricto del código activo para acotar la entrega a las tres Historias de Usuario asignadas a Acosta (**HU-01, HU-04, HU-06**) y la **Base Común Funcional**, asegurando que los compañeros puedan extender membresías y pagos sobre contratos estables.

---

## 2. Inventario de Alcance por Historia de Usuario

| Identificador | Descripción | Estado Técnico | Criterios Verificados |
| :--- | :--- | :--- | :--- |
| **BASE-01** | Arquitectura Limpia y Configuración `.env` | **VERIFICADO & PROBADO** | Spring Boot 3.4.3, importación de `.env`, Flyway migrations append-only, pool Hikari acotado a 10 conexiones, CORS desacoplado, manejo centralizado de excepciones con HTTP 400/401/403/404/409. |
| **HU-01** | Registrar Clientes y Consentimientos | **VERIFICADO & PROBADO** | Registro de socio adulto (edad ≥ 18 validada), unicidad documental compuesta `(country_code, document_type, document_number)` con respuesta 409 Conflict ante duplicados, consentimientos legales separados (Términos, Privacidad Ley N° 29733, Marketing Email/WhatsApp), endpoints `POST /api/v1/clients`, `GET /api/v1/clients` y `GET /api/v1/clients/{id}`. |
| **HU-04** | Roles y Gestión Institucional de Personal | **VERIFICADO & PROBADO** | Listado de cuentas para ADMIN, alta con contraseña hasheada en BCrypt, asignación de roles `ADMIN` y `RECEPCION`, inactivación de cuentas con revocación de tokens de sesión (`revokeSessionsForAccount`), protección crítica del último administrador activo (409 Conflict), control 401/403 por método y endpoint. |
| **HU-06** | Configurar Catálogo y Versiones de Planes | **VERIFICADO & PROBADO** | Catálogo de planes, validación estricta de duración mensual (exactamente 30 días), precios `NUMERIC(12,2) PEN`, versionado inmutable (`PlanVersion`) ante modificaciones, inactivación lógica (`PATCH /status`), consulta permitida a RECEPCION y mutaciones restringidas a ADMIN. |
| **HU-02 / HU-07 / HU-08** | Ficha extendida, Membresías y Pagos | **FUERA DEL INCREMENTO (RESERVADO)** | Retirado `MembershipController.java` del código activo. Tablas `memberships` y `payment_records` reservadas en Flyway `V1__init_schema.sql` para desarrollo de compañeros de equipo. |

---

## 3. Inventario Físico de Archivos

### Archivos Creados
- `src/main/resources/db/migration/V1__init_schema.sql`: Script DDL inicial de Flyway con restricciones de unicidad, foreign keys e índices.
- `src/main/java/com/gymvip/identity/api/AccountController.java`: Endpoints REST para administración institucional de personal (HU-04).
- `src/main/java/com/gymvip/identity/application/AccountManagementService.java`: Lógica de negocio de cuentas, asignación de roles, inactivación y validación del último administrador activo.
- `src/test/java/com/gymvip/identity/AccountManagementIntegrationTest.java`: Suite de pruebas de integración para HU-04 (6 escenarios).
- `.env.example`: Plantilla versionada de variables de entorno sin credenciales reales.
- `mvnw`, `mvnw.cmd`, `.mvn/`: Maven Wrapper configurado para portabilidad entre entornos.
- `README.md`: Documentación técnica completa del backend.
- `ESTADO_BACKEND_BASE.md`: Este documento de auditoría.

### Archivos Modificados
- `pom.xml`: Adición de dependencias oficiales de Flyway (`flyway-core`, `flyway-database-postgresql`).
- `src/main/resources/application.yml`: Configuración de perfiles `dev` y `prod`, directiva `spring.config.import=optional:file:./.env[.properties]`, validación DDL y zona horaria `America/Lima`.
- `src/main/java/com/gymvip/shared/security/SecurityConfig.java`: Desacoplamiento de orígenes CORS a variable de entorno y securización de `/api/v1/accounts/**`.
- `src/main/java/com/gymvip/shared/exceptions/GlobalExceptionHandler.java`: Manejador de excepciones de integridad de datos (`DataIntegrityViolationException`) con código HTTP 409 Conflict.
- `src/main/java/com/gymvip/identity/domain/Account.java`: Manejo seguro de mutación de roles para JPA `@ElementCollection` y setter de auditoría.
- `src/main/java/com/gymvip/identity/infrastructure/AccountRepository.java`: Consulta `@Query` para conteo de administradores activos (`countActiveAdmins`).
- `src/main/java/com/gymvip/identity/infrastructure/SessionRepository.java`: Método de revocación de sesiones por cuenta (`revokeByAccountId`).
- `src/main/java/com/gymvip/identity/application/AuthService.java`: Bloqueo de login a cuentas inactivas e integración con revocación de sesiones.
- `src/main/java/com/gymvip/clients/api/ClientController.java`: Retirada de mutación de contactos (`updateContacts`), manteniendo endpoints de alta y lectura mínima.
- `src/main/java/com/gymvip/plans/domain/Plan.java` y `PlanVersion.java`: Validaciones y relaciones bidireccionales.
- `src/main/java/com/gymvip/plans/application/PlanService.java`: Regla estricta de duración mensual (30 días exactos) y versionado inmutable.
- `src/test/java/com/gymvip/plans/PlanIntegrationTest.java`: Actualización de pruebas de integración de planes reflejando la regla de 30 días.
- `.gitignore`: Inclusión estricta de `.env` para prevenir fugas de secretos.

### Archivos Retirados del Código Activo
- `src/main/java/com/gymvip/memberships/api/MembershipController.java`: Retirado mediante `git rm` para delimitar el alcance de la entrega y evitar controladores mockeados o no asignados a Acosta.

---

## 4. Registro de Pruebas y Comprobaciones Reales

### Comando Ejecutado
```powershell
mvn test
```
*Herramientas:* Java 24.0.1 (Oracle OpenJDK), Maven 3.9.10, Spring Boot Test, H2 en modo PostgreSQL.

### Resultados Observados
- `com.gymvip.clients.ClientIntegrationTest`: **5 tests PASSED**
  - Registro de cliente exitoso con consentimientos.
  - Rechazo de menores de edad (HTTP 400).
  - Rechazo de duplicados de documento compuesto (HTTP 409).
  - Consulta de cliente por ID.
  - Listado paginado de clientes.
- `com.gymvip.identity.AccountManagementIntegrationTest`: **6 tests PASSED**
  - Acceso denegado a RECEPCION para listar cuentas (HTTP 403).
  - Acceso denegado a usuarios no autenticados (HTTP 401).
  - ADMIN lista cuentas con éxito (HTTP 200).
  - ADMIN crea nuevo usuario con rol RECEPCION (HTTP 200).
  - ADMIN cambia rol de usuario a ADMIN (HTTP 200).
  - ADMIN no puede inactivar al único administrador activo (HTTP 409 Conflict).
- `com.gymvip.plans.PlanIntegrationTest`: **4 tests PASSED**
  - Creación de plan exitosa con versionado inicial.
  - Rechazo de plan mensual que no cumpla 30 días exactos (HTTP 400).
  - Actualización de plan que genera nueva versión inmutable.
  - Inactivación lógica del plan.
- `com.gymvip.GymVipApplicationTests`: **1 test PASSED**
  - Carga completa del contexto Spring Boot y beans de configuración.

**Totales:** **16 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.**

---

## 5. Estado de Integración con PostgreSQL en Render

- **Configuración Realizada:** Driver PostgreSQL (`org.postgresql:postgresql`), dependencias Flyway PostgreSQL, perfil `prod` con `spring.jpa.hibernate.ddl-auto=validate` y Hikari pool acotado.
- **Carga de `.env`:** Comprobada mediante `application.yml` y `.env` de trabajo.
- **Conexión a BD Externa Render:** **NO VERIFICADA EN ESTA ESTACIÓN** debido a que no se suministraron credenciales activas de una instancia Render provisionada. Se provee `.env.example` con la sintaxis JDBC exacta `jdbc:postgresql://HOST:5432/DB?sslmode=require`. El sistema utiliza H2 persistente en modo PostgreSQL como BD de ensayo local.
