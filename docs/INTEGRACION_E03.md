# Integración E03 MA/RM

Se conserva el evaluador multicanal de E03: persistir APP antes de contactar proveedores, una clave única por periodo/regla/fecha/canal, fechas Lima y días posteriores seleccionados exactamente. Un fallo externo no revierte APP. Los reintentos de lectura se confirman contra backend; reevaluar no reenvía un intento FALLIDO. Nuevos comunicados/instalaciones/pedidos y revocación de sesiones de Acosta quedan integrados. FAQ solo devuelve publicaciones activas, sin respuestas inventadas ante fallos.

## Configuración

Docker local sigue usando el .env privado del directorio integrado. No se copió ni aplicó el .env remoto de Acosta. Para ejecución standalone: DB_JDBC_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, APP_COMMS_URL y APP_COMMS_TOKEN (o COMMS_SERVICE_TOKEN), usando exactamente el mismo token en comunicaciones. APP_ADMIN_INITIAL_PASSWORD/APP_RECEPCION_INITIAL_PASSWORD crean las cuentas solo si no existen; no cambian contraseñas de cuentas existentes. Configuración de ejemplo sin secretos en .env.example.

REMINDERS_SCHEDULE_ENABLED=true habilita el corte diario a las 10:00 America/Lima. Por defecto está deshabilitado para pruebas deterministas; la evaluación manual sigue disponible. WhatsApp y correo permanecieron en simulación durante QA. Las variables del backend no sustituyen google-services.json de Android.

## Migraciones e instalaciones existentes

V16 y V17 se mantienen exactamente como en E03 original; V18–V20 se aplican de forma aditiva. Se verificó la actualización local de V17 a V20 sin perder datos ni cambiar checksums anteriores. Hacer respaldo antes de actualizar cualquier base.

Acosta publicó variantes de V16 y V17 con checksums diferentes. Una base que ya las aplicó necesita reconciliación controlada ANTES de iniciar esta versión. scripts/reconcile-e03-history.sql reconoce exclusivamente ambos pares conocidos, verifica la estructura E03 y rechaza cualquier checksum desconocido. Solo actualiza los metadatos de esas dos versiones; no borra datos ni hace Flyway repair general. No se ejecutó contra ninguna base remota.

Ejemplo, después de respaldar y revisar la base destino:
~~~powershell
$env:PGOPTIONS='-c gymvip.allow_e03_history_reconciliation=on'
psql -h HOST -p PUERTO -U USUARIO -d BASE -v ON_ERROR_STOP=1 -f scripts/reconcile-e03-history.sql
Remove-Item Env:PGOPTIONS
~~~

Se probó sobre una copia local aislada con los checksums de Acosta: sin habilitación rechaza ejecución; habilitado concilia V16/V17 y deja el resto intacto. No aplicar a bases con estructura o historial diferentes; el script falla para permitir una revisión específica. Las credenciales remotas permanecen privadas.

## QA

Pruebas de servicios FAQ, lectura, recordatorios y seguridad seleccionadas correctas. Recorrido web/móvil ejecutado con fixture Sofía Demo, publicación/edición/desactivación de FAQ, proveedor detenido, APP independiente, lectura persistida y cero duplicados al reevaluar. Los comunicados públicos nuevos pueden leerse y los avisos privados continúan aislados por cliente. Historial distingue simulación/fallo/lectura. No se acredita envío WhatsApp, correo o FCM real ni aceptación total de la épica.

## Consulta de programación — 20/11/2026

GET /api/v1/reminders/schedule (ADMIN/RECEPCION según seguridad existente) expone enabled, dailyTime=10:00 y timeZone=America/Lima del programador actual. Consulta de solo lectura: no modifica reglas guardadas ni habilita el programador, y no altera el reporte /api/v1/channels/status. Prueba parametrizada verifica ambos estados. Verificado en el entorno local: reglas activas y programación desactivada.


## Reporte de canales y alcance — 20/11/2026

Los canales de esta entrega son WHATSAPP y APP. EMAIL sale de configuración, evaluaciones futuras y seguimiento de recordatorios; sus registros existentes permanecen en base sin borrado. La configuración heredada filtra EMAIL al consultar/evaluar; guardar acepta solo APP/WHATSAPP y no habilita otro canal si antes solo había EMAIL. No se modifica la recuperación de cuentas ni sus mecanismos de correo.

GET /api/v1/channels/status del backend devuelve channels.whatsapp y channels.app. WhatsApp se consulta al microservicio con X-Service-Key y un timeout de lectura de 3s. Se permiten DISABLED, SIMULATION, UNCONFIGURED, CONFIGURED_REAL; una respuesta incompleta, inconsistente o un error da UNVERIFIED (No se pudo verificar). No se exponen secretos, URLs privadas ni excepciones del proveedor. CONFIGURED_REAL requiere modo production, proveedor meta, canal habilitado, phoneNumberId numérico, accessToken no vacío/placeholder y versión API válida; no verifica credenciales contra Meta, plantillas aprobadas ni entrega. connectionVerified siempre false. WHATSAPP_ENABLED=false desactiva el adaptador; no intenta envío real. Desde la integración del 23/11, producción incompleta falla explícitamente (UNCONFIGURED/BLOCKED), sin fallback simulado.

APP informa inbox.status=AVAILABLE y requiresFirebase=false. push.enabled refleja habilitación; desde el 23/11 su status es CONFIGURED_REAL solo con FirebaseMessaging inicializado en el servidor, UNCONFIGURED sin credenciales o DISABLED si está desactivado. connectionVerified permanece false. Un google-services.json del cliente no certifica el servidor FCM. Seguimiento y Configuración comparten el reporte correcto; no usan health del backend como verificación de WhatsApp. El resultado histórico se consulta por separado en PostgreSQL y no se reescribe al consultar estados de canales. Las plantillas y su aprobación local se conservan.

QA: 96 pruebas web/build, 60 backend seleccionadas y 27 comunicaciones sin fallos. Verificación real local con microservicio detenido: UNVERIFIED en WhatsApp, bandeja disponible y lectura/fallos históricos visibles; recuperación a SIMULATION después del arranque. Baseline de IDs/estados/lecturas WhatsApp/APP idéntico tras actualizar. Base conserva 3 registros EMAIL históricos; endpoint de seguimiento solo muestra APP/WHATSAPP. Solicitud EMAIL en configuración rechazada400; reevaluación con cero nuevos. Backend/comunicaciones/PostgreSQL saludables. Evidencias outputs/canales-antes.json, canales-reporte.json y capturas58–59. No se enviaron mensajes reales ni se activó Firebase.


## Integración de novedades de Acosta — 23/11/2026

Rama compartida: feature/e03-integracion-actualizaciones-qquerar. La referencia feature/e03-integracion-ma-rm-qquerar permanece intacta en los cuatro repositorios. Se integran los commits remotos revisados, sin modificar main ni las ramas personales de Acosta:

| Repositorio | Referencia E03 anterior | Acosta incorporado |
| --- | --- | --- |
| Backend | 653cba0 | 59e6ec5 |
| Comunicaciones | 29b0339 | 2005ad1 |
| Web | 71b5a31 | 4f5cf32 |
| Móvil | 94f5402 | 2e01fec |

### Contratos conciliados

- E03 conserva solo APP y WHATSAPP. Los registros históricos EMAIL no se borran; la recuperación de cuentas mantiene su infraestructura de correo independiente.
- Se conserva FAQ publicada, revisión de propuestas, desactivación, caché con error y contacto Recepción. APP se guarda antes del envío externo; lectura privada por socio y clave única por periodo/regla/fecha/canal. Reevaluar no reenvía fallidos.
- La API devuelve JSON por defecto aunque Firebase incorpore convertidores XML. Los formatos solicitados explícitamente, como las exportaciones, siguen disponibles. La anticipación acepta 3/5/7/15, en concordancia con la interfaz; la validación rechaza otros valores y elementos nulos.
- WhatsApp consulta al microservicio con clave interna y timeout. Si falla: UNVERIFIED. Producción incompleta se reporta UNCONFIGURED/BLOCKED y el adaptador rechaza el envío; se elimina el antiguo fallback silencioso a simulación. La simulación explícita continúa sin enviar mensajes incluso si hay credenciales. CONFIGURED_REAL no prueba conexión ni entrega.
- La bandeja funciona sin Firebase. Push integra Firebase Admin y el registro Android opcional; requiere cuenta de servicio privada del servidor, google-services.json del Android correspondiente y dispositivo autorizado. El worker verifica token activo, cliente activo y consentimiento antes del envío. Credenciales presentes no equivalen a entrega comprobada. No se activó Firebase real durante QA.
- Se conservan las novedades de catálogo, prioridades, perfil de personal/AUDITOR, sesiones, reportes, incidencias, archivo de instalaciones, pedidos y enlaces Culqi. Los favoritos devuelven su registro canónico de forma idempotente. Las promociones superpuestas se resuelven con prioridad y desempate determinista.
- Móvil mantiene SYSTEM carbón/azul/blanco y las paletas DARK/LIGHT previas. Conserva BLUE_DARK/BLUE_LIGHT y preferencias por cuenta. Debug permite Firebase deshabilitado; release exige Firebase oficial y firma privada, sin reemplazarla por la firma debug.
- Seguimiento sigue siendo el único historial operativo; resultados históricos independientes del estado actual de canales. Plantillas locales solo afectan la vista previa y su revisión/aprobación en ese navegador, no Meta ni el contenido efectivo del servidor. No se habilitan reintentos reales incompletos.

### Base y configuración de cada compañero

Se conserva la configuración local privada y el número de pruebas de Recepción en la compilación local, sin copiar el .env de Acosta ni publicar secretos. Cada entorno aporta sus propios .env y archivos privados. Backend y comunicaciones deben compartir el mismo token interno. Firebase del servidor usa FIREBASE_SERVICE_ACCOUNT_BASE64; no sustituye google-services.json ni las claves de firma Android. Culqi permanece sin cobros reales en este entorno.

Respaldo local antes de aplicar V21–V28: gymvip-before-actualizaciones.dump, fuera de Git. Base existente actualizada a V28 y base aislada de pruebas creada desde cero. V1–V20 se conservan; para bases de Acosta con checksums históricos V16/V17 diferentes sigue aplicando la conciliación explícita y verificada descrita arriba/en la documentación del backend. Nunca ejecutar repair genérico ni alterar la base de un compañero sin revisar y respaldar su historial. No se intervino ninguna base remota.

### Verificación de esta entrega

Backend: 260 pruebas; comunicaciones: 32; web: 100 y build; móvil: 53 pruebas de escritorio y APK Android debug instalado. Los reportes finales no presentan fallos. Pruebas de proveedor Meta/FCM/Culqi usan dobles o configuración local: no acreditan entregas ni cobros reales. No se compiló iOS ni se acredita una publicación Android en tienda.

Recorrido manual en entorno local, 23/11/2026:

1. FAQ QA2311: crear/publicar; guardar propuesta mantuvo la respuesta vigente en móvil; aprobar cambió la respuesta; desactivar la retiró tras actualizar.
2. Con backend detenido: el móvil mostró error, última respuesta guardada, Reintentar y contacto WhatsApp. Al recuperar backend se actualizó correctamente. La FAQ de prueba quedó desactivada.
3. Se ajustó exclusivamente la membresía de Sofia Demo para vencer el 26/11/2026 (periodo 28/10–27/11 exclusivo). El segundo socio de pruebas estaba vencido cinco días. Reglas con anticipación 3/5/7/15 y posteriores 1/5; solo APP/WHATSAPP.
4. Con comunicaciones detenido, Evaluar ahora registró cuatro avisos/intentos para dos periodos: APP disponible y WHATSAPP fallido. No se enviaron mensajes a números reales.
5. El móvil actualizó Avisos, abrió el recordatorio del 26/11 y confirmó lectura en backend. Seguimiento mostró Leído por el socio y su fecha. Al recuperar comunicaciones a SIMULATION, los intentos fallidos conservaron ese resultado.
6. Segunda evaluación: dos periodos, cero avisos nuevos y cuatro omitidos/repetidos. Se conserva trazabilidad, sin duplicados ni reenvío del fallo.

Evidencia local: capturas 63–68 y logs actualizaciones-* en outputs/ del espacio integrado. Backend 8081, comunicaciones 8085, web 5173 y emulador Android local. Programación diaria deshabilitada para pruebas; reglas habilitadas no significan cron habilitado. El emulador API36 usa compatibilidad 16KB para bibliotecas nativas heredadas. Próximo recorrido pendiente: reintento controlado WhatsApp con autorización, idempotencia y resultado real; no está implementado por esta integración.

Verificación adicional de canales: microservicio detenido → UNVERIFIED; bandeja AVAILABLE; recuperación → SIMULATION; historial completo idéntico antes/después; push UNCONFIGURED y connectionVerified=false. Configuración persistida exclusivamente WHATSAPP/APP. Evidencia outputs/69-integracion-canales.json. Contenedor de pruebas detenido al terminar; servicios de la aplicación permanecen encendidos.
