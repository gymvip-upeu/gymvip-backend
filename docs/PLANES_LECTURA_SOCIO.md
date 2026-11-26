# Consulta de planes del socio

26 de noviembre de 2026.

`GET /api/v1/member-app/plans` requiere sesión con rol CLIENTE. Devuelve una lista de planes ACTIVO con `id`, `name`, `description`, `durationDays`, `pricePen`, `benefits` y `conditions`. No acepta escrituras ni filtros de administración, y no expone versiones, auditoría o planes inactivos. Usa `PlanService.listPlans("ACTIVO")` y conserva los permisos de `/api/v1/plans`.

Se utiliza para la hoja modal de Membresía de gymvip-mobile. No cambia la contratación, los precios, el esquema ni el catálogo de suplementos.

Validación: `MemberPlanReadControllerTest` verifica filtro ACTIVO y campos comerciales. En PostgreSQL 17 local: CLIENTE GET member-app/plans 200; CLIENTE GET plans 403; CLIENTE POST plans 403; ADMIN GET plans?status=ACTIVO 200. Imagen local `gymvip-backend:membresia-planes`, basada en develop 087a044 más este controlador. Se mantiene un único backend activo en 8081, reutilizando la misma base de compatibilidad y las credenciales existentes.
