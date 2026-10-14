# Diagrama de Entidad-Relación de la Base Activa (ERD) — GYM VIP
**Responsable Base:** André Acosta (Sprint 1)  
**Motor:** PostgreSQL 16+ (Render / Local) / H2 compatibilidad PostgreSQL en dev  
**Estrategia de Migración:** Flyway (`V1__init_schema.sql`, `V2__add_member_code_to_clients.sql`) con `ddl-auto: validate`  

---

## 1. Modelo Entidad-Relación Activo (Mermaid)

```mermaid
erDiagram
    ACCOUNTS ||--o{ ACCOUNT_ROLES : "tiene roles asignados"
    ACCOUNTS ||--o{ SESSIONS : "mantiene sesiones activas"
    CLIENTS ||--|{ DOCUMENTS : "identificado por"
    PLANS ||--|{ PLAN_VERSIONS : "posee versiones tarifarias"
    AUDIT_EVENTS }o--|| ACCOUNTS : "registra acciones de"

    ACCOUNTS {
        uuid id PK "Identificador único (UUIDv4)"
        varchar email UK "Correo institucional normalizado"
        varchar password_hash "Hash seguro BCrypt con salt"
        varchar status "ACTIVO | INACTIVO"
        timestamp created_at "Fecha y hora de alta en servidor"
        timestamp updated_at "Última modificación"
    }

    ACCOUNT_ROLES {
        uuid account_id PK, FK "Referencia a accounts.id"
        varchar role PK "ADMIN | RECEPCION"
    }

    SESSIONS {
        uuid id PK "Identificador único de sesión"
        varchar token UK "Token seguro URL-safe Base64"
        uuid account_id FK "Referencia a accounts.id"
        varchar ip_address "IP cliente observada"
        timestamp expires_at "Fecha límite de vigencia"
        timestamp created_at "Fecha de emisión"
    }

    CLIENTS {
        uuid id PK "Identificador interno (UUIDv4)"
        varchar member_code UK "Código único legible (ej. MAR-8Q2L7K)"
        varchar first_name "Nombres del socio"
        varchar last_name "Apellidos del socio"
        date birth_date "Fecha nacimiento (>= 18 años)"
        varchar phone "Teléfono celular de contacto"
        varchar email "Correo opcional del socio"
        timestamp created_at "Fecha y hora de registro de servidor"
        timestamp updated_at "Última actualización"
    }

    DOCUMENTS {
        uuid id PK "Identificador del documento"
        uuid client_id FK "Referencia al socio (clients.id)"
        varchar type "DNI | CE | PASAPORTE"
        varchar number "Número de documento (string con ceros)"
        timestamp created_at "Fecha registro"
        timestamp updated_at "Fecha modificación"
    }

    PLANS {
        uuid id PK "Identificador del plan (UUIDv4)"
        varchar name UK "Nombre normalizado único"
        varchar description "Descripción de cobertura"
        int duration_days "Duración en días (30 para mensual)"
        numeric price_pen "Precio en Soles PEN (NUMERIC 12,2)"
        varchar benefits "Detalle de beneficios"
        varchar status "ACTIVO | INACTIVO"
        int version "Versión vigente (control concurrencia)"
        timestamp created_at "Fecha creación"
        timestamp updated_at "Fecha modificación"
    }

    PLAN_VERSIONS {
        uuid id PK "Identificador de la instantánea"
        uuid plan_id FK "Referencia al plan padre (plans.id)"
        int version_number "Número correlativo de versión"
        varchar name "Nombre congelado del plan"
        varchar description "Descripción congelada"
        int duration_days "Duración congelada en días"
        numeric price_pen "Precio congelado en PEN"
        varchar benefits "Beneficios congelados"
        varchar status "Estado en el momento del snapshot"
        timestamp created_at "Fecha y hora de creación de la versión"
    }

    AUDIT_EVENTS {
        uuid id PK "Identificador de evento"
        varchar actor_email "Usuario autenticado que realizó la acción"
        varchar action "CREATE_CLIENT, CREATE_PLAN, UPDATE_ROLE, etc."
        varchar target_type "Client, Plan, Account"
        varchar target_id "ID del recurso afectado"
        varchar details "Detalle auditable sin datos sensibles"
        varchar ip_address "Dirección IP del cliente"
        timestamp created_at "Timestamp inmutable"
    }
```

---

## 2. Restricciones Físicas y Llaves Únicas Clave

| Tabla | Restricción / Índice | Columnas | Comportamiento / HU |
| :--- | :--- | :--- | :--- |
| `clients` | `uk_clients_member_code` (UNIQUE) | `member_code` | Garantiza unicidad del código de socio (`MAR-XXXXXX`). Reintento automático en generación. (HU-01) |
| `documents` | `uk_documents_type_number` (UNIQUE) | `type, number` | Unicidad documental compuesta en PostgreSQL. Devuelve HTTP 409 Conflict ante duplicados concurrentes. (HU-01) |
| `plans` | `uk_plans_name` (UNIQUE) | `LOWER(name)` | Impide registrar dos planes con el mismo nombre comercial. (HU-06) |
| `plan_versions` | `uk_plan_versions_plan_version` (UNIQUE) | `plan_id, version_number` | Asegura que cada versión de un plan sea única e inmutable en el tiempo. (HU-06) |
| `accounts` | `uk_accounts_email` (UNIQUE) | `email` | Correo institucional único para cada colaborador. (HU-04) |
| `sessions` | `uk_sessions_token` (UNIQUE) | `token` | Token único de sesión revocado inmediatamente ante cambio de rol o inactivación. (HU-04) |

---

## 3. Tablas Físicas Conservadas sin Uso Activo (Legacy / Roadmap Compañeros)

En cumplimiento estricto con la consigna de **no reescribir ni eliminar migraciones históricas** (`V1__init_schema.sql`) y **no ejecutar `DROP TABLE`**, las siguientes tablas físicas residen en el esquema PostgreSQL pero **NO forman parte del código activo de la base Acosta**:

1. `memberships`: Diseñada para asignación de membresías a clientes, suscripciones y renovaciones. Se transfiere limpia a los compañeros para el Sprint correspondiente.
2. `payment_records`: Diseñada para el registro de transacciones, pasarela y cobros.
3. `consents`: Conservada históricamente; la interfaz de alta de cliente ya no genera consentimientos legales ficticios (conforme a la última decisión de UX).
