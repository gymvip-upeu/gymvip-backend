# Contratos de API REST — GYM VIP (v1)

**Protocolo:** HTTPS  
**Formato de intercambio:** `application/json; charset=utf-8`  
**Encabezados globales requeridos en mutaciones:**
- `Content-Type: application/json`
- `X-Correlation-Id: <uuid>` (opcional en cliente, generado por backend si falta)
- `Idempotency-Key: <uuid>` (requerido en asignación de membresía y cobro)

---

## 1. Esquema Estándar de Errores

Toda respuesta con código HTTP 4xx o 5xx devuelve la siguiente estructura:

```json
{
  "timestamp": "2026-10-11T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "DOCUMENT_ALREADY_EXISTS",
  "message": "Ya existe un cliente registrado con el documento DNI 72819201 en PE.",
  "path": "/api/v1/clients",
  "correlationId": "8f9b8c2e-4b2a-4f51-b8ef-123456789abc",
  "details": [
    {
      "field": "documentNumber",
      "message": "Número de documento duplicado"
    }
  ]
}
```

---

## 2. Endpoints de Autenticación y Sesión

### `POST /api/v1/auth/login`
- **Permiso:** Público
- **Descripción:** Autentica a un usuario y genera una sesión activa segura.
- **Request:**
  ```json
  {
    "email": "recepcion@vipcenterfit.pe",
    "password": "Password123!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "token": "sess_8f9a2b...",
    "accountId": "b1a2c3d4-...",
    "email": "recepcion@vipcenterfit.pe",
    "roles": ["RECEPCION"],
    "expiresAt": "2026-10-11T12:00:00Z"
  }
  ```
- **Errores:** `401 Unauthorized` (Credenciales inválidas o cuenta bloqueada).

### `POST /api/v1/auth/logout`
- **Permiso:** Requiere sesión
- **Response (204 No Content)**

---

## 3. Endpoints de Clientes (HU-01, HU-02)

### `POST /api/v1/clients`
- **Permiso:** `RECEPCION`, `ADMIN`, `SUPERADMIN`
- **Descripción:** Registra un nuevo socio adulto con sus datos personales y consentimiento explícito.
- **Request:**
  ```json
  {
    "firstName": "Carlos Eduardo",
    "lastName": "Mendoza Quispe",
    "birthDate": "1995-08-14",
    "documentType": "DNI",
    "documentNumber": "72819201",
    "countryCode": "PE",
    "phone": "+51987654321",
    "email": "carlos.mendoza@ejemplo.pe",
    "consents": {
      "termsAccepted": true,
      "privacyAccepted": true,
      "marketingEmailAccepted": false,
      "marketingWhatsAppAccepted": true,
      "termsVersion": "v1.0",
      "privacyVersion": "v1.0"
    }
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "id": "c1d2e3f4-5a6b-7c8d-9e0f-112233445566",
    "firstName": "Carlos Eduardo",
    "lastName": "Mendoza Quispe",
    "documentType": "DNI",
    "documentNumber": "72819201",
    "status": "ACTIVO",
    "createdAt": "2026-10-11T10:35:00Z"
  }
  ```
- **Errores:**
  - `400 Bad Request`: Menor de 18 años o datos obligatorios faltantes.
  - `409 Conflict`: `DOCUMENT_ALREADY_EXISTS`.

### `GET /api/v1/clients`
- **Permiso:** `RECEPCION`, `ADMIN`, `SUPERADMIN`
- **Query Params:** `query` (búsqueda parcial por nombre o documento), `page` (0..n), `size` (default 20).
- **Response (200 OK):**
  ```json
  {
    "content": [
      {
        "id": "c1d2e3f4-...",
        "firstName": "Carlos Eduardo",
        "lastName": "Mendoza Quispe",
        "documentType": "DNI",
        "documentNumber": "72819201",
        "phone": "+51987654321",
        "email": "carlos.mendoza@ejemplo.pe",
        "status": "ACTIVO"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
  ```

---

## 4. Endpoints de Planes de Membresía (HU-06)

### `GET /api/v1/plans`
- **Permiso:** `RECEPCION`, `ADMIN`, `SUPERADMIN`
- **Query Params:** `status` (opcional: `ACTIVO`, `INACTIVO`, default: todos para admin, solo activos para recepción).
- **Response (200 OK):**
  ```json
  [
    {
      "id": "p1a2b3c4-...",
      "name": "Plan VIP Mensual",
      "description": "Acceso total al área de pesas y cardio.",
      "durationDays": 30,
      "pricePen": 120.00,
      "benefits": "Acceso ilimitado, casillero de cortesía",
      "status": "ACTIVO",
      "version": 1,
      "createdAt": "2026-10-11T08:00:00Z"
    }
  ]
  ```

### `POST /api/v1/plans`
- **Permiso:** `ADMIN`, `SUPERADMIN` (denegado a `RECEPCION` con HTTP 403).
- **Request:**
  ```json
  {
    "name": "Plan VIP Trimestral",
    "description": "Promoción 3 meses de entrenamiento continuo.",
    "durationDays": 90,
    "pricePen": 320.00,
    "benefits": "Acceso ilimitado + evaluación física mensual"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "id": "p2b3c4d5-...",
    "name": "Plan VIP Trimestral",
    "durationDays": 90,
    "pricePen": 320.00,
    "status": "ACTIVO",
    "version": 1,
    "createdAt": "2026-10-11T10:40:00Z"
  }
  ```
- **Errores:** `400 Bad Request` (precio o duración <= 0), `403 Forbidden` (si lo intenta `RECEPCION`), `409 Conflict` (nombre duplicado).

### `PUT /api/v1/plans/{id}`
- **Permiso:** `ADMIN`, `SUPERADMIN`
- **Descripción:** Modifica un plan e incrementa su versión sin alterar membresías históricas suscritas con versiones anteriores.
- **Response (200 OK)**

### `PATCH /api/v1/plans/{id}/status`
- **Permiso:** `ADMIN`, `SUPERADMIN`
- **Request:** `{"status": "INACTIVO"}`
- **Response (200 OK)**

---

## 5. Endpoints de Asignación y Cobro (HU-07, HU-08)

### `POST /api/v1/memberships`
- **Permiso:** `RECEPCION`, `ADMIN`, `SUPERADMIN`
- **Headers:** `Idempotency-Key: 11112222-3333-4444-5555-666677778888`
- **Request:**
  ```json
  {
    "clientId": "c1d2e3f4-...",
    "planId": "p1a2b3c4-...",
    "startDate": "2026-10-11",
    "payment": {
      "amountPen": 120.00,
      "paymentMethod": "YAPE",
      "externalReference": "OPE-992812",
      "notes": "Pago recibido en mostrador"
    }
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "membershipId": "m1n2o3p4-...",
    "clientId": "c1d2e3f4-...",
    "planName": "Plan VIP Mensual",
    "startDate": "2026-10-11",
    "endDate": "2026-11-10",
    "status": "VIGENTE",
    "paymentId": "pay_998811...",
    "amountPaidPen": 120.00,
    "idempotencyKey": "11112222-3333-4444-5555-666677778888"
  }
  ```
- **Errores:** `400 Bad Request` (monto no coincide con precio del plan), `409 Conflict` (cliente ya posee membresía vigente).
