# Contrato de membresías — Alexander, Sprint 1

Implementación propuesta del 26/10/2026. Pendiente de acuerdo con Jorge y ratificación comercial de Wagner; no acredita aceptación ni integración de HU-09/11/12.

## API

Todos los endpoints requieren token y rol ADMIN o RECEPCION. CLIENTE recibe 403.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/memberships/payment-methods` | Medios realmente habilitados por configuración |
| POST | `/api/v1/memberships/quote` | Resumen autoritativo sin escritura |
| POST | `/api/v1/memberships/sales` | Contratar/renovar + cobro + auditoría en una transacción |
| GET | `/api/v1/memberships/clients/{clientId}/history` | Periodos cobrados, versiones, condiciones y constancias |

Resumen:
```json
{"clientId":"UUID", "planId":"UUID", "operation":"ENROLL"}
```
`operation` admite ENROLL o RENEW. La respuesta incluye membershipId (null en alta), planVersionId, planName, benefits, pricePen, currentEndExclusive, startDate, lastIncludedDate, endExclusive, timezone, policy y fingerprint.

Cobro: cabecera `Idempotency-Key` de 8–100 caracteres `[A-Za-z0-9_-]`.
```json
{
  "clientId":"UUID", "planId":"UUID", "operation":"RENEW",
  "quoteFingerprint":"SHA256_DEL_RESUMEN", "amountPen":120.00,
  "paymentMethod":"YAPE", "reference":"REFERENCIA_PRESENCIAL"
}
```
El importe se compara con el precio de la versión activa en backend. No acepta cuotas, deudas ni planes distintos de 30 días en este flujo. El medio no efectivo requiere referencia. POS solo aparece si el gimnasio lo configura en `MEMBERSHIP_PAYMENT_METHODS`; los valores admitidos son EFECTIVO,YAPE,PLIN,TRANSFERENCIA,POS. Los cuatro primeros son la configuración inicial propuesta.

La respuesta 200 contiene membershipId, periodId, paymentId, clientId, planVersionId, planName, benefits, policy, startDate, lastIncludedDate, endExclusive, amountPen, currency=PEN, paymentMethod, reference, registeredBy, paidAtUtc y receiptNumber. Un reintento exitoso devuelve el mismo resultado; no genera nuevos identificadores. La constancia VIP-UUID es interna y no tributaria.

Errores: 400 validación/importe/medio/referencia, 401 sin sesión, 403 rol insuficiente, 404 cliente/plan inexistente, 409 conflicto de contrato/clave/resumen. Si cambian el día local, plan o fin contractual después del resumen, hay 409 y se requiere revisar un resumen nuevo. No se cobran nuevas fechas de forma silenciosa.

## Datos para Jorge

- `memberships`: contrato único por cliente y servicio GYM; start_date original, end_date como fin de la última cobertura contratada, plan_version_id inicial. Un cliente vencido conserva el contrato y usa RENEW. CANCELADA u otro estado no habilitado rechaza renovación.
- `membership_periods`: periodos históricos con plan_version_id y policy_text propios, fechas inicio incluidas/fin excluidas. Una renovación nunca sobrescribe un periodo anterior.
- `payment_records`: originales económicos; los cobros nuevos llevan period_id único, cuenta del trabajador y timestamp UTC. Sin endpoints de edición o borrado.
- `membership_operations`: clave por actor y ámbito de venta de membresías, huella canónica de la solicitud y respuesta durable. No contiene datos de tarjetas.
- `MembershipDates`: utilidad de fechas en America/Lima. HU-09 debe considerar cada periodo pagado; no deducir acceso solo del estado almacenado ni de una cuenta habilitada.

Predicado de cobertura para el futuro verificador (parámetro `today` de Lima): `period.start_date <= today AND today < period.end_date`, existencia de pago vinculado y contrato no cancelado. No usar únicamente el intervalo agregado del contrato: una renovación tardía puede dejar un hueco sin cobertura. El cálculo de POR_VENCER y su umbral siguen a cargo de HU-09.

Ejemplos de referencia: pago 26/10 → inicio 26/10, último incluido 24/11, fin exclusivo 25/11. Renovación anticipada → periodo 25/11–24/12 incluido. Si paga vencido el 10/12 → periodo 10/12–08/01 incluido. Una fecha igual al fin exclusivo ya está fuera de ese periodo.

## Transacción y concurrencia

Una venta adquiere bloqueo transaccional PostgreSQL de clave+actor y bloqueo de la fila del cliente. Recalcula la cobertura, toma bloqueo compartido del plan, valida resumen/precio e inserta contrato/periodo/pago/auditoría/resultado idempotente dentro de la misma transacción Spring. Un fallo incluso después de insertar pago revierte todo.

Dos reintentos de la misma clave devuelven una venta. Dos operaciones distintas simultáneas sobre el mismo resumen: una confirma y la otra recibe 409; con un nuevo resumen la segunda puede añadir otro periodo. La clave no sustituye una confirmación comercial de una nueva compra.

## Migraciones y compatibilidad

V1/V2 heredadas permanecen intactas. V3 extiende las tablas reservadas y agrega periodos/operaciones; V4 guarda las condiciones comerciales por periodo. Se eligieron porque el remoto inspeccionado solo tenía V1/V2. Coordinar V3/V4 con Jorge antes de integrar; no se afirma una reserva confirmada por él.

V3 conserva filas preexistentes mediante periodos heredados. No atribuye automáticamente periodos a pagos antiguos ambiguos (period_id null), ni inventa condiciones anteriores (policy_text null). El historial de esta API presenta cobros con vínculo explícito al periodo. Si hay datos heredados en las tablas reservadas, conciliarlos antes de habilitar este flujo. Contratos duplicados por cliente/servicio provocan fallo transaccional de la migración para revisión manual, sin borrar registros.

## Pendientes de cierre

Ratificar corte de fechas, medios/referencias/pago completo y cambio de plan con Wagner; revisión del contrato y numeración de migraciones con Jorge/André; integración real de HU-09/11/12; ejecución en PostgreSQL 17 de Render con credenciales privadas y esquema exclusivo. No cambiar Render ni fusionar a develop/main en esta entrega.
