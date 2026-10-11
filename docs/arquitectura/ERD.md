# Diagrama Entidad-Relación (ERD) — GYM VIP

**Motor:** PostgreSQL / Esquema Relacional  
**Convención:** Claves primarias autoincrementales o UUIDs; columnas en `snake_case`; índices únicos para restricciones de negocio.

---

## Diagrama Lógico y Físico (Mermaid)

```mermaid
erDiagram
  ACCOUNTS ||--o| CLIENTS : "vinculada a"
  ACCOUNTS ||--o{ SESSIONS : "tiene"
  ROLES ||--o{ ACCOUNT_ROLES : "asigna"
  ACCOUNTS ||--o{ ACCOUNT_ROLES : "posee"

  CLIENTS ||--o{ DOCUMENTS : "identificado por"
  CLIENTS ||--o{ CONSENT_RECORDS : "registra"
  CLIENTS ||--o{ MEMBERSHIPS : "suscribe"

  PLANS ||--|{ PLAN_VERSIONS : "contiene"
  MEMBERSHIPS ||--|| PLAN_VERSIONS : "snapshot de"
  MEMBERSHIPS ||--|{ PAYMENT_RECORDS : "respaldada por"

  ACCOUNTS ||--o{ AUDIT_EVENTS : "ejecuta acción"

  ACCOUNTS {
    uuid id PK
    string email UK
    string password_hash
    string status
    boolean mfa_enabled
    datetime created_at
    datetime updated_at
  }

  ROLES {
    string id PK "SUPERADMIN, ADMIN, RECEPCION, CLIENTE"
    string description
  }

  ACCOUNT_ROLES {
    uuid account_id PK,FK
    string role_id PK,FK
  }

  SESSIONS {
    string token PK
    uuid account_id FK
    datetime expires_at
    datetime created_at
    string user_agent
    string ip_address
  }

  CLIENTS {
    uuid id PK
    string first_name
    string last_name
    date birth_date
    string email
    boolean email_verified
    string phone
    boolean phone_verified
    string status "ACTIVO, INACTIVO"
    uuid account_id FK "nullable"
    datetime created_at
    datetime updated_at
  }

  DOCUMENTS {
    uuid id PK
    uuid client_id FK
    string country_code "PE"
    string document_type "DNI, CE, PASAPORTE"
    string document_number
    boolean is_primary
    datetime created_at
  }

  CONSENT_RECORDS {
    uuid id PK
    uuid client_id FK
    string policy_type "TERMS, PRIVACY, MARKETING_EMAIL, MARKETING_WHATSAPP"
    string policy_version
    boolean choice
    string channel "WEB_REGISTRATION, IN_PERSON"
    string ip_address
    datetime timestamp_utc
  }

  PLANS {
    uuid id PK
    string name UK
    string description
    int duration_days
    decimal price_pen
    string benefits
    string status "ACTIVO, INACTIVO"
    datetime created_at
    datetime updated_at
  }

  PLAN_VERSIONS {
    uuid id PK
    uuid plan_id FK
    int version_number
    string name
    int duration_days
    decimal price_pen
    string benefits
    datetime effective_from
  }

  MEMBERSHIPS {
    uuid id PK
    uuid client_id FK
    uuid plan_version_id FK
    date start_date
    date end_date
    string status "VIGENTE, VENCIDA, CANCELADA"
    string idempotency_key UK "nullable"
    datetime created_at
  }

  PAYMENT_RECORDS {
    uuid id PK
    uuid membership_id FK
    decimal amount_pen
    string currency "PEN"
    string payment_method "EFECTIVO, YAPE, PLIN, TRANSFERENCIA, POS"
    string external_reference "nullable"
    uuid registered_by_account_id FK
    datetime payment_timestamp_utc
    string notes
  }

  AUDIT_EVENTS {
    uuid id PK
    uuid actor_account_id FK "nullable"
    string action
    string entity_name
    string entity_id
    string before_state_json
    string after_state_json
    datetime timestamp_utc
    string origin_ip
  }
```

---

## Restricciones Clave de Integridad

1. **Unicidad de Documento de Identidad:**
   `CREATE UNIQUE INDEX uk_document_per_country ON documents (country_code, document_type, document_number);`
   Garantiza que ningún cliente pueda registrarse duplicando un DNI o Pasaporte.
2. **Unicidad de Nombre de Plan:**
   `CREATE UNIQUE INDEX uk_plan_name ON plans (LOWER(name));`
   Evita planes con el mismo nombre comercial.
3. **Idempotencia de Membresía:**
   `CREATE UNIQUE INDEX uk_membership_idempotency ON memberships (idempotency_key);`
   Protege contra el doble cobro o doble creación por pulsaciones repetidas o reintentos de red.
4. **No Nulidad Financiera:**
   `CHECK (price_pen > 0 AND duration_days > 0)` en planes.
   `CHECK (amount_pen > 0)` en registros de pago.
