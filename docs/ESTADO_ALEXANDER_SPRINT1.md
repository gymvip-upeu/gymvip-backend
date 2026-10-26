# Estado de Alexander — Sprint 1

Fecha: 26/10/2026. Rama en ambos repositorios: `feature/s1-alexander-hu07-hu08-hu10`, desde la entrega publicada de André. Base backend `afe6f84c1903a9cb7fd3161fbbe267e5450e8699`; frontend `b1f3ae7684d6f2f6896b083219c66a141aa76ebb`.

## Estado de publicación e IntelliJ

Ambos proyectos abiertos en ventanas separadas de IntelliJ IDEA 2025.2. Commit funcional local backend `94ad383ff2d373c03d910f6663dab6afc9cba68c`; frontend `b61cdb84439b0b37811975e3c15d8d732aa1e06c`.

**PUBLICACIÓN BLOQUEADA:** `git push --set-upstream origin feature/s1-alexander-hu07-hu08-hu10` recibió HTTP 403 en ambos repositorios: acceso denegado a la cuenta `alexgm-A11`. La conexión GitHub de la app confirmó `permissions.push=false` en ambos. `git ls-remote --heads` no encontró la rama de Alexander tras el intento. No hay enlaces remotos de estos commits ni igualdad HEAD/remoto que acreditar.

Un administrador de la organización debe otorgar acceso Write a esa cuenta en ambos repositorios (y aceptar la invitación si corresponde), o configurar una cuenta ya autorizada. Después se podrá repetir el push de la rama propia y verificar hashes. No se cambiaron permisos, credenciales, protecciones ni ramas de otros integrantes. La implementación, las pruebas y los commits locales están conservados.

## Incremento implementado

HU-07: contratación de cliente existente con plan activo de 30 días, resumen/calendario, precio del backend y snapshot de condiciones. HU-08: cobro completo presencial en PEN, medios configurables, referencia no efectiva, trabajador, fecha UTC y constancia interna. HU-10: renovación anticipada/vencida, cambio de plan para el periodo añadido e historial de periodos/cobros.

Venta y renovación persisten contrato, periodo, pago, auditoría y respuesta idempotente en una transacción. El bloqueo por cliente evita solapamientos; clave por actor y huella impide reutilizar la operación con otro cuerpo. Resúmenes desactualizados reciben conflicto. La UI conserva una operación incierta en sessionStorage y permite reintentar con la misma clave/cuerpo, incluso después de recargar.

Ver contrato API/datos en `CONTRATO_MEMBRESIAS_S1.md` del backend y matriz de los 54 criterios en `MATRIZ_ALEXANDER_S1.md` de ambos repositorios.

## Validación real

- 42 pruebas backend sin fallos: 23 heredadas y 19 nuevas; PostgreSQL local 18.4, esquema `gymvip_test_alexander` con Flyway/Hibernate/datasource alineados. La conexión diagnóstica también usa ahora el datasource aislado.
- Casos: rollback después de pago; replay, claves con otro cuerpo/actor; simultaneidad; plan/cliente inactivos o inexistentes; importe/referencia/POS inválidos; renovación anticipada, vencida, límite de día, cambio de plan y condiciones históricas.
- Build frontend correcto. E2E real vía Vite 5173 → Spring 8081 → PostgreSQL local: contratación en efectivo y renovación Yape; no se simuló la API.
- Socio ficticio E2E `E2E-ALEX-001`: dos pagos de PEN 120 y dos periodos consecutivos. Constancias `VIP-a31933b3-b1e4-424e-befc-daa601c90822` y `VIP-a775df5a-4d4c-4b4a-a5b9-d9969847037f`. Persistencia comprobada tras reinicio de backend y PostgreSQL. Captura fuera de Git: `.local-gymvip/membresias-e2e.png`.
- Revisión desktop y móvil de 390px: lectura, formulario y constancia utilizables; historial con scroll interno. No se atribuye aceptación visual a Wagner.
- Esquema `public` rechazado por la protección de tests. No se limpiaron fixtures globales ni se modificaron datos del equipo.

## Pendientes / límites de cierre

1. Wagner: ratificar inicio/fin y zona Lima, pago completo/referencias, medios reales y cambio de plan; revisión visual/aceptación no recibida.
2. Jorge/André: revisar contrato de cobertura y migraciones V3/V4 antes de integrar. HU-09/11/12 aún no integradas; no se afirma cierre conjunto ni aprobación del equipo.
3. Accesos privados a Render: no disponibles en esta copia. Validar en PostgreSQL 17 con el esquema exclusivo; las pruebas locales en 18.4 no sustituyen esa comprobación. Flyway 10 informa advertencia por motor 18 posterior al máximo probado.
4. POS: soporte configurable implementado y deshabilitado inicialmente. No se afirma que el gimnasio lo tenga configurado ni que el flujo POS real esté aceptado.
5. Datos heredados de tablas reservadas: no se inventa correspondencia de pagos antiguos con periodos ni condiciones históricas. Conciliar si existen antes de habilitar el flujo.
6. Revisión de código por compañeros, capacidad/PE y futura integración a develop/main pendientes. No se realizaron merges ni cambios de plan/configuración Render.

## Reproducción

Backend: completar privadamente DB_JDBC_URL/DB_USERNAME/DB_PASSWORD; establecer TEST_DB_SCHEMA=gymvip_test_alexander y ejecutar `./mvnw.cmd test`. El guard rechaza public, esquemas ajenos y currentSchema en la URL de pruebas antes de migrar. Para desarrollo usar PORT=8081. En el entorno local de validación se pasaron explícitamente schema de Hikari/Hibernate/Flyway al arrancar; no se crearon .env con credenciales públicas.

Frontend: `npm ci`, `npm run build`, `npm run dev`. Entrar con cuenta de personal, abrir Membresías y cobros, buscar cliente, escoger operación/plan, consultar resumen y confirmar el cobro ficticio solo en un entorno de prueba.

Los archivos generados, la base temporal y los logs quedan fuera de ambos repositorios. Las referencias remotas de entrega se verifican después del push y se entregan en el chat/documento general del workspace para evitar hashes autorreferenciales.

Comprobación adicional frontend: 5 pruebas unitarias del transporte aprobadas (respuesta truncada, constancia incompleta, conflicto, preservación de clave/cuerpo y fallo de red), ejecutadas con npm run test:membership. Estas pruebas usan transporte simulado; el E2E descrito arriba sí utilizó API y PostgreSQL reales. Herramientas locales verificadas: Java 22.0.2 (compilación release 21), Node 22.20.0, npm 10.9.3, Maven Wrapper 3.9.10. Lectura del historial tras reinicio también comprobada con token real de RECEPCION; public conserva cero tablas.
