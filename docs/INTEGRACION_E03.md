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
