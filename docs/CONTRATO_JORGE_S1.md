# Contrato de Jorge — HU-02, HU-09, HU-11 y HU-12

Implementación del 26/10/2026. Integra la base de André `afe6f84` y la dependencia de Alexander `982934b` en la rama de Jorge, sin merges a develop/main. Las convenciones de inicio incluido/fin excluido, America/Lima y últimos 7 días se implementan coherentemente con la rama de Alexander; su ratificación comercial por Wagner sigue pendiente.

## Clientes y auditoría

`GET /api/v1/clients/{id}` ahora incluye `version` numérica. Los demás campos heredados permanecen. ADMIN y RECEPCION pueden consultar y actualizar.

`PUT /api/v1/clients/{id}` recibe todos los campos editables y la versión leída:

```json
{"firstName":"Ana","lastName":"Pérez","countryCode":"PE","documentType":"DNI","documentNumber":"12345678","phone":null,"email":null,"version":0}
```

País/tipo/número corresponden al documento principal. Se conserva su ID, código de socio, fecha de nacimiento y registro. Nombres/apellidos/documento son obligatorios; correo y celular opcionales mantienen la regla de la base. Longitudes y formatos se validan en servidor; DNI PE tiene 8 dígitos y la unicidad sigue siendo país/tipo/número. Cambiar correo/celular revoca su marca de verificación previa.

Devuelve ficha completa con nueva `version`. Una actualización sin cambios no genera auditoría ni incrementa versión. Versión obsoleta o carrera concurrente: 409, sin cambios parciales ni auditoría del intento perdedor. La UI conserva el formulario y permite recargar explícitamente la ficha. Cambios válidos y filas de auditoría pertenecen a una sola transacción. El registro genérico de auditoría heredado también se reutiliza.

`GET /api/v1/clients/{id}/changes?page=0&size=20`: exclusivamente ADMIN. Respuesta paginada con `changeId`, `clientId`, `actor`, `field`, `before`, `after`, `changedAtUtc`. Cada campo modificado ocupa una fila; `changeId` agrupa una operación. Orden descendente por fecha, con desempate estable. Tamaño 1–100. No contiene contraseñas/tokens. RECEPCION recibe 403.

V5 agrega versión a clientes y tabla `client_changes`; no modifica V1–V4. Coordinar cualquier migración nueva con el equipo.

## Cobertura y verificación

ADMIN y RECEPCION:

- `GET /api/v1/coverage/clients/{clientId}`: verificación manual/consulta de ficha, sin escritura de asistencia.
- `GET /api/v1/coverage?query=&state=ALERTAS&planId=&from=&to=&page=0&size=20`: seguimiento paginado y filtros. Omitir parámetros vacíos de ID/fechas.

Estados: VIGENTE, POR_VENCER, VENCIDO, CANCELADA, PENDIENTE, SIN_MEMBRESIA. `ALERTAS` filtra POR_VENCER/VENCIDO y `TODOS` los incluye todos. Estados desconocidos, rangos invertidos y paginación inválida: 400. Cliente inexistente: 404. Sin autenticación: 401; CLIENTE: 403. El endpoint es de lectura y no concede excepciones de gracia.

La respuesta incluye identidad, documento, estado de cliente/contrato por separado, plan, `startDate`, `lastIncludedDate`, `endExclusive`, `daysRemaining`, `state`, `allowed`, `reason`, `checkedAt`, `timezone`, `upcomingDays`. El reloj es el mismo bean Clock que usa Alexander. La zona se reutiliza desde MembershipDates. No hay scheduler ni cálculo de permisos en el navegador.

Solo se consideran periodos con pago vinculado explícitamente, importe igual al precio de su versión y moneda PEN. Los periodos contiguos o superpuestos se consolidan en bloques de cobertura; los huecos entre renovaciones tardías no se rellenan. El bloque actual determina vigencia; sin bloque actual se presenta el último vencido o el primero futuro. La fecha final y el plan mostrado corresponden al extremo final del bloque seleccionado. Los estados históricos VENCIDO/POR_VENCER del contrato no sustituyen la evaluación de las fechas; CANCELADA y otros estados no habilitados niegan ingreso. El cliente debe estar ACTIVO.

Ingreso permitido solo cuando la fecha de Lima pertenece a un bloque pagado de un contrato habilitado. Se consulta en transacción de lectura REPEATABLE_READ para evitar mezclar periodos/pagos durante una renovación. Renovaciones continuas muestran la fecha final extendida sin duplicar cliente. Filtros de fechas son inclusivos sobre el último día válido; orden por fin exclusivo ascendente y cliente como desempate.

`MEMBERSHIP_UPCOMING_DAYS=7` es el valor propuesto de negocio, configurable de 1 a 30. Las pruebas del sprint usan 7. La lista usa el mismo cálculo que ficha/verificación. La paginación se aplica tras calcular los bloques; esta implementación carga periodos de lectura en memoria, adecuada al conjunto de prueba pero sin prueba de carga para volúmenes grandes. No se afirma optimización de escala.

## Frontend

Se reutilizan React 18, Vite, contexto de autenticación, cliente HTTP, estilos/tokens y componentes comunes. Clientes conserva búsqueda y añade paginación/ficha con edición e historial ADMIN. Vigencias conserva filtros al actualizar; una venta confirmada emite `gymvip-membership-changed` y la vista también refresca al recuperar foco. Verificación elimina el resultado anterior antes de consultar; errores técnicos o respuestas incompletas nunca conservan un permiso anterior. La búsqueda descarta respuestas obsoletas.

Sin QR, cámara, check-in, asistencia, mensajes, cancelación operativa, reportes económicos ni trabajo móvil KMP en este incremento. Revisión comercial/visual de Wagner y comprobación en Render siguen pendientes; las pruebas realizadas usan PostgreSQL 17.6 local, no acreditan la versión o disponibilidad de Render.
