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

GET /api/v1/channels/status del backend devuelve channels.whatsapp y channels.app. WhatsApp se consulta al microservicio con X-Service-Key y un timeout de lectura de 3s. Se permiten DISABLED, SIMULATION, UNCONFIGURED, CONFIGURED_REAL; una respuesta incompleta, inconsistente o un error da UNVERIFIED (No se pudo verificar). No se exponen secretos, URLs privadas ni excepciones del proveedor. CONFIGURED_REAL requiere modo production, proveedor meta, canal habilitado, phoneNumberId numérico, accessToken no vacío/placeholder y versión API válida; no verifica credenciales contra Meta, plantillas aprobadas ni entrega. connectionVerified siempre false. WHATSAPP_ENABLED=false desactiva el adaptador; no intenta envío real. Producción incompleta conserva el fallback simulado anterior y se reporta UNCONFIGURED.

APP informa inbox.status=AVAILABLE y requiresFirebase=false. push.enabled solo refleja habilitación; su status es UNCONFIGURED hasta integrar y validar el emisor real del servidor, o DISABLED si está desactivado. Un google-services.json del cliente no certifica el servidor FCM. Seguimiento y Configuración comparten el reporte correcto; no usan health del backend como verificación de WhatsApp. El resultado histórico se consulta por separado en PostgreSQL y no se reescribe al consultar estados de canales. Las plantillas y su aprobación local se conservan.

QA: 96 pruebas web/build, 60 backend seleccionadas y 27 comunicaciones sin fallos. Verificación real local con microservicio detenido: UNVERIFIED en WhatsApp, bandeja disponible y lectura/fallos históricos visibles; recuperación a SIMULATION después del arranque. Baseline de IDs/estados/lecturas WhatsApp/APP idéntico tras actualizar. Base conserva 3 registros EMAIL históricos; endpoint de seguimiento solo muestra APP/WHATSAPP. Solicitud EMAIL en configuración rechazada400; reevaluación con cero nuevos. Backend/comunicaciones/PostgreSQL saludables. Evidencias outputs/canales-antes.json, canales-reporte.json y capturas58–59. No se enviaron mensajes reales ni se activó Firebase.
