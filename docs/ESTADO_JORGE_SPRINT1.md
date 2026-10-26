# Estado de Jorge — Sprint 1

Fecha de validación: 26/10/2026. Responsable del incremento: Jorge. HU-02, HU-09, HU-11 y corte manual de HU-12 implementadas en backend/frontend; aceptación del sprint pendiente de Wagner. Se conserva Java21/Spring Boot3.4.3/JPA/Security/Flyway/PostgreSQL y React18/Vite6.4.4, sin dependencias de aplicación nuevas.

## Base y alcance

Rama propia: `feature/s1-jorge-hu02-hu09-hu11-hu12` en ambos repositorios, descendiente de André. Se incorporó por avance rápido la rama de Alexander como dependencia funcional, conservando su autoría/historial: backend `982934b095a2c8b1466467bbcf66532c9b951e89`, frontend `e287547913ba368361610cb5875abb43b5fd50fe`. No se modifican ramas de compañeros, main/develop ni Render.

- HU-02: edición de nombres/apellidos/documento/celular/correo, unicidad, control optimista de versión, transacción y auditoría antes/después/autor/fecha solo ADMIN. Búsqueda, ficha y paginación reales.
- HU-09: cálculo único de bloques de cobertura pagados, mismo reloj/zona que contratación/renovación; límites exactos, 30 días, sin gracia, cancelación consultada y huecos de renovación respetados.
- HU-11: listado y filtros por cliente/plan/estado/último día válido, orden urgente, paginación, actualización y persistencia de filtros al navegar a renovar/regresar, una fila por cliente.
- HU-12: búsqueda/selección manual de identidad y verificación en servidor; sin plan/vencido/cancelado/inactivo/pago incompleto rechazan; errores técnicos o respuestas inválidas no conservan permiso anterior.

V5 agrega control de versión de clientes y `client_changes`. V1–V4 permanecen intactas. Contratos y decisiones: [CONTRATO_JORGE_S1.md](CONTRATO_JORGE_S1.md). Los 72 CA/DoD/DoR originales se registran en [MATRIZ_JORGE_S1.md](MATRIZ_JORGE_S1.md).

## Validación realizada

Entorno real de esta entrega: Windows, Microsoft JDK21.0.12.1, Maven Wrapper3.9.10, Node22.16.0 y npm11.5.2 preparados localmente; PostgreSQL17.6 portable en loopback:55432, base `gymvip_jorge`, esquema exclusivo `gymvip_test_jorge`. No se usó H2 ni se accedió a Render. El lockfile no cambió; React18.3.1, React DOM18.3.1 y plugin React4.7.0 son sus versiones resueltas heredadas.

Resultados:

- Suite completa backend: **58 pruebas, 0 fallos, 0 errores, 0 omitidas**; incluye las pruebas heredadas de André/Alexander.
- Comprobación final pertinente tras explicitar las columnas del INSERT de auditoría: **18 pruebas, 0 fallos/errores**, subconjunto de las anteriores. No se suman a 58.
- Paquete Spring Boot ejecutable generado correctamente. En Windows fue necesario detener el backend local antes de reemplazar su JAR bloqueado; se reempaquetó y arrancó correctamente.
- Frontend: **11 pruebas**, todas correctas, con el runner Node ya utilizado por Alexander; `npm run build` correcto.
- Chrome contra frontend/API/PostgreSQL reales: edición e historial ADMIN, 409 conserva formulario, sin membresía rechaza, pago confirmado permite, error de red retira permiso anterior, listado real, renovación sin duplicados/filtros conservados y filtros conservados al navegar a Membresías/regresar. RECEPCION abre edición sin historial ADMIN. Sin errores JavaScript. Capturas revisadas en escritorio y ancho390px; tabla con desplazamiento interno y sin desbordamiento de página.
- Persistencia tras detener y reiniciar el backend: nombre editado, versión2, dos cambios de campos auditados y cobertura pagada hasta fin exclusivo 25/12/2026 releídos correctamente en un cliente ficticio.
- Consulta de information_schema:15 tablas solo en `gymvip_test_jorge`; ninguna tabla en public ni esquema de Alexander en esta base local. Flyway V1–V5 con success=true.

La protección de tests exige exactamente `gymvip_test_jorge` en datasource/Hibernate/Flyway, sin `currentSchema` en URL y sin esquemas adicionales. `IsolationGuardTest` demuestra rechazo de public, vacío, gymvip_test y esquema de Alexander antes de migrar. No se realizan limpiezas globales; fixtures son nuevos por caso.

## Reproducción

Completar variables privadas fuera del control de versiones. No colocar credenciales PostgreSQL en Vite. Java y Node/npm deben estar disponibles en la terminal. Desde backend:

```powershell
# Usar exclusivamente un PostgreSQL de pruebas autorizado y nunca public.
$env:TEST_DB_SCHEMA='gymvip_test_jorge'
# Definir TEST_DB_JDBC_URL, TEST_DB_USERNAME y TEST_DB_PASSWORD privadamente.
.\mvnw.cmd test
# Detener cualquier instancia que use target/*.jar antes de empaquetar en Windows.
.\mvnw.cmd -DskipTests package
```

Desde frontend:

```powershell
npm ci
npm test
npm run build
npm run dev
```

Backend local8081 y proxy Vite5173. Las capturas y recibos de validación se entregan fuera de Git; no se publican .env, logs crudos, dumps, binarios ni salidas de compilación. Las pruebas fuente y el contrato quedan versionados para reproducir los casos.

## Pendientes de aceptación

- Ratificar con Wagner corte Lima/inicio incluido/fin excluido/7 días y revisar diseño. Estas reglas siguen siendo propuestas de la guía, implementadas de forma coherente con Alexander; no se inventa su aprobación.
- Revisión del contrato y migración V5 por André/Alexander, capacidad/PE del equipo y revisión de código. No se acredita coordinación formal por publicar documentación.
- Probar contra PostgreSQL17 de Render cuando se entreguen credenciales privadas y permisos para un esquema exclusivo; la prueba local no equivale a validar Render17.11.
- Integración posterior a develop/main por el equipo. El incremento está integrado funcionalmente en la rama de Jorge; no se fusiona a esas ramas en esta etapa.
- Sin pruebas de carga para grandes volúmenes; el listado calcula bloques en memoria antes de paginar.

No incluye QR/asistencia, mensajes, cancelación operativa, pase diario, reportes S2, móvil KMP ni cambios de Figma sin observaciones/destino proporcionados. No se certifica aprobación de Wagner ni despliegue.
