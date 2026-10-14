# Documento de Transferencia Técnica (Handoff) — Base Sprint 1
**Autor:** André Acosta (Responsable Base Funcional)  
**Destinatarios:** Compañero 2 (Alexander) & Compañero 3  
**Alcance Transferido:** HU-01 (Registro de Clientes), HU-04 (Personal y Roles), HU-06 (Catálogo de Planes)  
**Fecha:** Octubre 2026  

---

## 1. Resumen de la Base Funcional Lista para Extensión

Se entrega una arquitectura monolítica modular en Backend (Spring Boot 3.4.3 / Java 21) y Frontend (React 18 / Vite / Vanilla CSS Luxury Dark/Gold) completamente desacoplada y funcional.
Ambos proyectos se encuentran conectados mediante variables de entorno (.env), permitiendo conmutar entre bases de datos PostgreSQL (local o Render) sin tocar una sola línea de código Java o JavaScript.

---

## 2. Contratos y Modelos Reutilizables para el Equipo

### 2.1 Módulo Clientes (`com.gymvip.clients`)
- **Para Compañero 2 / 3 (Membresías y Asignación de Planes):**
  - Para asociar una membresía o consultar un socio, inyectar el puerto de entrada:
    `com.gymvip.clients.application.port.in.SearchClientsUseCase`
  - Métodos disponibles:
    - `getClientById(UUID id)`: Devuelve el modelo puro `Client`.
    - `searchClients(String query, Pageable pageable)`: Devuelve página de clientes.
  - El cliente expone su código único `memberCode` (ej. `MAR-8Q2L7K`) e identificador interno `UUID id`.
  - **Importante:** La edición/desactivación avanzada de clientes y el historial médico corresponden a la HU-02 según el reparto del equipo. Se dejan los puertos limpios para dicha extensión.

### 2.2 Módulo Planes (`com.gymvip.plans`)
- **Para Compañero 2 (Membresías y Contratos Económicos):**
  - Inyectar el puerto de entrada:
    `com.gymvip.plans.application.port.in.ManagePlanUseCase`
  - Métodos disponibles:
    - `getPlanById(UUID id)`: Obtiene el plan con su versión vigente.
    - `listPlans(String status)`: Lista planes activos para suscripción.
  - **Invariante Crítico:** La tabla `plan_versions` contiene instantáneas inmutables con su precio (`price_pen`) y duración (`duration_days`). Al crear una membresía en su módulo, deben enlazar la membresía a la **versión específica del plan** (`plan_version_id`) para que futuros cambios de tarifa del gimnasio no alteren los cobros retroactivos de los socios existentes.

### 2.3 Módulo Identidad y Seguridad (`com.gymvip.identity` y `com.gymvip.shared.security`)
- **Autenticación y Sesión:**
  - Inyectar `AuthUseCase` para validar tokens o consultar el usuario autenticado:
    `getCurrentUser(String token)`
  - Control de Acceso: Todos los endpoints sensibles deben anotarse con:
    `@PreAuthorize("hasRole('ADMIN')")` o `@PreAuthorize("hasAnyRole('RECEPCION', 'ADMIN')")`
  - La seguridad rechaza automáticamente con **401 Unauthorized** peticiones sin cabecera `Authorization: Bearer <token>` y con **403 Forbidden** peticiones con rol insuficiente.

### 2.4 Módulo de Auditoría Transversal (`com.gymvip.audit`)
- Para registrar eventos de auditoría en nuevas funcionalidades (pagos, membresías, accesos), inyectar:
  `com.gymvip.audit.application.port.in.AuditUseCase`
  - Llamar a:
    `recordEvent(actorEmail, action, targetType, targetId, details)`

---

## 3. Puntos de Extensión en Frontend (`gymvip-frontend`)

1. **Estructura por Características (`src/features/`):**
   - Para añadir **Membresías**: Crear la carpeta `src/features/memberships/` siguiendo la misma convención:
     - `domain/` (modelos puros e invariantes)
     - `infrastructure/` (cliente API consumiendo `src/shared/http/httpClient.js`)
     - `presentation/` (vistas, modales y tablas)
2. **Navegación (`src/app/layout/AppLayout.jsx`):**
   - Descomentar o añadir el ítem de membresías en el arreglo `navItems`:
     ```javascript
     { id: 'memberships', label: 'Membresías', icon: '🎫', title: 'Gestión de Membresías y Pagos' }
     ```
   - Renderizar la vista dentro del switch contextual de `AppLayout.jsx`.
3. **Estilos y Tokens:**
   - Todo nuevo componente debe usar las variables CSS definidas en `src/index.css` (`var(--color-gold)`, `var(--color-surface-panel)`, `var(--color-border)`, etc.).
   - No introducir Tailwind ni paletas moradas/azules.

---

## 4. Reglas de Base de Datos y Migraciones Flyway

1. **Append-Only Obligatorio:**
   - La última migración aplicada en esta base es `V2__add_member_code_to_clients.sql`.
   - Cualquier nueva tabla o columna requerida para HU subsiguientes debe crearse como `V3__nombre_descriptivo.sql` en `src/main/resources/db/migration/`.
2. **Tablas Físicas Reservadas:**
   - Las tablas `memberships` y `payment_records` ya existen físicamente en la BD desde `V1__init_schema.sql`. Pueden usarlas directamente o ajustarlas en `V3` mediante `ALTER TABLE`.
   - **PROHIBIDO:** Ejecutar `DROP TABLE` o editar scripts ya aplicados (`V1` y `V2`).

---

## 5. Variables de Entorno para Nuevos Entornos

### Backend (`gymvip-backend/.env`)
```properties
SPRING_PROFILES_ACTIVE=dev
PORT=8080
DB_JDBC_URL=jdbc:postgresql://HOST_EXTERNO:5432/NOMBRE_BD?sslmode=require
DB_USERNAME=usuario_render
DB_PASSWORD=secreto_render
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
APP_ADMIN_INITIAL_PASSWORD=Admin123!
APP_RECEPCION_INITIAL_PASSWORD=Recepcion123!
```

### Frontend (`gymvip-frontend/.env`)
```properties
VITE_API_BASE_URL=
```
*(Dejar vacío en desarrollo local para aprovechar el proxy de Vite en puerto 8080).*
