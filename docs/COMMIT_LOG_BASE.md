# Registro de Commits Locales — Base Funcional Acosta
**Proyecto:** GYM VIP (VIP CENTER FIT S.A.C.)  
**Responsable:** André Acosta (Sprint 1)  
**Organización:** `gymvip-upeu`  
**Rama:** `base-acosta`  
**Fecha:** 14 de Octubre de 2026  

---

## 1. Tabla de Correlación de Commits Backend y Frontend

| Repositorio | Hash Real | Rama | Mensaje de Commit | Historias / Alcance | Verificación Relevante |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `gymvip-backend` | `cd1a19e` | `base-acosta` | `refactor(architecture): migrar backend a Clean Architecture y delimitar base Acosta (HU-01, HU-04, HU-06)` | HU-01, HU-04, HU-06, Infraestructura | `.\mvnw test` (16 pruebas ejecutadas y aprobadas al 100%) |
| `gymvip-frontend` | `e5d591f` | `base-acosta` | `refactor(architecture): migrar frontend a Clean Architecture y shell luxury Dark & Gold con sidebar plegable` | HU-01, HU-04, HU-06, UI Shell | `npm run build` (Vite 6 compiló en 3.85s con 48 módulos transformados) |
| `gymvip-backend` | `e803709` | `base-acosta` | `docs(base): documentar Clean Architecture, diagramas C4, ERD, modelos y handoff de entrega` | Documentación Backend | Revisión de diagramas Mermaid C4/ERD/Modelos |
| `gymvip-frontend` | `3a1a260` | `base-acosta` | `docs(base): documentar estado del frontend, Clean Architecture por features y handoff de entrega` | Documentación Frontend | Revisión de tokens Dark & Gold y Handoff |

---

## 2. Detalle de Cambios por Commit

### 2.1 Backend: `cd1a19e` — `refactor(architecture)`
- **`clients/`:**
  - Migración completa a Clean Architecture: `domain/model/Client.java`, `Document.java`, `MemberCodeGenerator.java` (genera `MAR-8Q2L7K` con reintento ante colisión).
  - Puertos e implementaciones de casos de uso: `RegisterClientUseCase` (`RegisterClientService`), `SearchClientsUseCase` (`SearchClientsService`).
  - Adaptadores y entidades JPA: `ClientPersistenceAdapter`, `ClientJpaEntity`, `DocumentJpaEntity`.
  - DTOs y controladores: `ClientController`, `RegisterClientRequest` (sin casillas de consentimiento ficticias).
  - Unicidad compuesta en BD con `409 Conflict`.
- **`plans/`:**
  - Migración a Clean Architecture: `domain/model/Plan.java`, `PlanVersion.java` (instantánea inmutable).
  - Regla de negocio: planes con denominación mensual deben tener exactamente 30 días.
  - Puertos e implementaciones: `ManagePlanUseCase` (`ManagePlanService`), `PlanPersistenceAdapter`.
  - Control de concurrencia optimista mediante `@Version` de JPA y `saveAndFlush`.
- **`identity/`:**
  - `domain/model/Account.java`, `Session.java`.
  - `StaffAccountUseCase` (`StaffAccountService`), `AuthUseCase` (`AuthService`).
  - Seguridad RBAC estricta: `401 Unauthorized` ante peticiones sin sesión y `403 Forbidden` ante roles insuficientes.
  - Salvaguarda del último administrador habilitado (`LastAdminProtectionException` -> HTTP 400).
  - Revocación de sesiones al cambiar rol o inactivar cuenta.
- **`audit/`:**
  - Registro de eventos en modelo puro `AuditEvent` y persistencia mediante `AuditPersistenceAdapter`.
- **`shared/`:**
  - Carga explícita de `.env` (`spring.config.import=optional:file:./.env[.properties]`).
  - Migración Flyway `V2__add_member_code_to_clients.sql`.
- **Retiro de código ajeno:**
  - Se retiraron del código activo los controladores y servicios de membresías y pagos. Las tablas físicas correspondientes en `V1__init_schema.sql` se mantienen reservadas para los compañeros.

### 2.2 Frontend: `e5d591f` — `refactor(architecture)`
- **Clean Architecture por Features:**
  - `src/features/clients/`: `ClientsPage.jsx`, `ClientRegisterModal.jsx` (solo datos personales, sin casillas de consentimiento legal ficticias), `ClientDetailModal.jsx` (Ficha del socio con código `MAR-XXXXXX`).
  - `src/features/plans/`: `PlansPage.jsx` (filtros, tarjetas, botones de mutación para ADMIN, vista de solo lectura para RECEPCION), `PlanModal.jsx` (crear y editar tarifa con versión incremental).
  - `src/features/staff/`: `StaffPage.jsx` (protegido para ADMIN, modal de rol, confirmación para inactivar, alerta de protección de último admin), `StaffModal.jsx`.
  - `src/features/home/`: `HomePage.jsx` (KPIs calculados desde APIs reales: socios, planes activos, personal en servicio).
  - `src/features/auth/`: `LoginModal.jsx` (diseño luxury dark & gold con accesos rápidos demo).
- **Shell y Sistema Visual:**
  - `src/app/layout/AppLayout.jsx`: Sidebar colapsable (256px expandido / 76px colapsado) con botón solo icono `PanelLeftClose`/`PanelLeftOpen`, `aria-expanded`, `aria-controls` y persistencia en `localStorage`.
  - Drawer móvil responsivo (< 768px) con backdrop y cierre por Escape.
  - Cabecera compacta de 68px con glassmorphism moderado.
- **Infraestructura Compartida:**
  - `src/shared/http/httpClient.js`: Centraliza `VITE_API_BASE_URL`, inyección de token y manejador de 401.

### 2.3 Backend & Frontend: `e803709` y `3a1a260` — `docs(base)`
- Diagramas Mermaid válidos: C4 (C1, C2, C3 en `docs/C4_BASE_ACTUAL.md`), ERD con llaves y cardinalidades en `docs/ERD_BASE_ACTUAL.md`, Dinámica y Trazabilidad en `docs/MODELOS_BASE_ACTUAL.md`.
- Handoff detallado para el Compañero 2 y Compañero 3 en `docs/HANDOFF_BASE_SPRINT1.md`.
- Estados de cumplimiento y DoD en `docs/ESTADO_BACKEND_BASE.md` y `docs/ESTADO_FRONTEND_BASE.md`.
- `README.md` exhaustivos en ambos proyectos.
