# Avisos y notificaciones Android — E03

Rama: `feature/e03-push-movil-qquerar`, basada en la integración de Acosta. WhatsApp y web conservan su comportamiento. Firebase no sustituye la bandeja ni el consentimiento del socio.

## Recorrido

1. Un recordatorio APP o comunicado publicado crea su aviso y un trabajo PUSH con `inbox_item_id`. Los fallos de WhatsApp no revierten APP.
2. El dispositivo autenticado registra su token en `POST /api/v1/member-app/devices`. El backend determina socio y sesión desde la autenticación, nunca desde un propietario enviado por el teléfono.
3. El emisor comprueba token activo, sesión vigente, cliente/cuenta activos, consentimiento y destino vigente. Cada trabajo por dispositivo conserva propietario y sesión; un token reasignado no recibe trabajos del socio anterior.
4. FCM recibe únicamente datos de navegación: `type=GYMVIP_INBOX`, `accountId`, `itemId`, `sessionTag` (SHA256 del identificador de sesión; no es el JWT). No contiene nombre, vencimiento, plan ni texto privado.
5. Android valida cuenta y sesión antes de mostrar una alerta genérica. Al tocarla abre Avisos y consulta contenido de esa cuenta. No confirma lectura automáticamente.
6. Cerrar sesión bloquea localmente los mensajes y cancela alertas antes de solicitar desvinculación y cierre remoto. Si no hay red, no puede revocar de inmediato el registro del servidor: Android rechaza los mensajes de esa sesión; la sesión remota queda limitada por su vencimiento. En línea, el logout invalida la sesión y el FK impide su uso por el worker.

## Configuración real

Crear un proyecto Firebase propiedad del equipo, registrar los paquetes Android que se compilen y habilitar Firebase Cloud Messaging HTTP v1. Para el emulador local se usa `com.gymvip.mobile.local`; el paquete habitual es `com.gymvip.mobile`. Ambos deben pertenecer al mismo proyecto Firebase que el servidor.

Android: colocar el archivo oficial apropiado en `composeApp/src/debug/google-services.json` o `composeApp/google-services.json` y compilar con `-Pgymvip.firebase=true`. Para la variante local añadir `-Pgymvip.debugApplicationId=com.gymvip.mobile.local -Pgymvip.backendUrl=http://10.0.2.2:8081`. No inventar un JSON ni copiar una cuenta de servicio dentro del APK. Release conserva sus requisitos de Firebase oficial y firma privada.

Servidor: elegir una cuenta de servicio del proyecto con los permisos necesarios de FCM. Configurar **una** opción: `FIREBASE_SERVICE_ACCOUNT_BASE64`, `FIREBASE_SERVICE_ACCOUNT_FILE` o `GOOGLE_APPLICATION_CREDENTIALS` apuntando a un archivo privado. `FIREBASE_PROJECT_ID` permite verificar que pertenece al proyecto esperado. En Docker las rutas deben existir dentro del contenedor mediante un montaje privado de solo lectura. El compose local admite base64 mediante el `.env` privado. Las claves no se publican en Git, logs ni variables VITE.

Sin credenciales no se inicializa el emisor y la bandeja continúa. Con una configuración inválida el servidor falla al iniciar para evitar aparentar que FCM está configurado. `CONFIGURED_REAL` solo acredita inicialización del SDK; no acredita entrega.

## Estados e idempotencia

Migración aditiva V29: sesión por dispositivo y metadatos de destino por trabajo. Desactiva tokens heredados hasta el próximo registro autenticado; omite trabajos anteriores sin destino verificable. Respaldar antes de aplicarla.

Outbox: `PENDING` → `EXPANDED` (distribución a dispositivos) → trabajos `ACCEPTED`, `FAILED` o `SKIPPED`. `ACCEPTED` significa aceptado por FCM, nunca entregado/leído. Hasta tres intentos con espera creciente; `UNREGISTERED` desactiva el token. Trabajos de más de 24 horas caducan sin borrar el aviso. Las claves únicas, bloqueo transaccional y deduplicación Android por cuenta/sesión/aviso evitan alertas repetidas. No se promete entrega exactamente una vez a través de redes externas.

Las actualizaciones de pedidos de Acosta se conservan: utilizan `GYMVIP_ORDERS` y abren los pedidos autenticados del catálogo, con las mismas verificaciones de propietario/sesión. Los recordatorios y comunicados usan exclusivamente `GYMVIP_INBOX` con el identificador exacto de su aviso. Los trabajos desconocidos sin destino se omiten con motivo explícito.

## Pruebas y límites

Backend: aislamiento entre socios, sesión ajena, alta idempotente, cambio de cuenta compartiendo token, baja restringida al propietario/sesión, sesión cerrada, consentimiento revocado, distribución, resultado aceptado sin lectura ni repetición y errores acotados sin secretos. Pruebas con PostgreSQL aislado y FirebaseMessaging de prueba; no equivalen a transporte FCM real.

Android: pruebas instrumentadas del permiso denegado conservando caché, duplicados, propietario/sesión incorrectos y bloqueo local al salir. Las pruebas del ViewModel verifican destino correcto, caché propia sin conexión, reintento tras recuperar red y lectura explícita. Los cambios de permiso se ejecutan fuera del proceso instrumentado para evitar que Android interrumpa la prueba al revocar un permiso.

Pendiente de completar con el proyecto real: recepción FCM con app abierta, segundo plano y proceso terminado normalmente; toque en cada aviso; rotación de token y cierre de sesión con la conexión disponible/interrumpida. Android no garantiza recepción inmediata, especialmente en ahorro de batería; una app detenida forzosamente requiere volver a abrirse. Denegar permiso nunca borra ni marca leído el aviso.

Referencia: https://firebase.google.com/docs/cloud-messaging/android/receive-messages y https://firebase.google.com/docs/admin/setup.

## Pausa y punto de reanudación — 23/11/2026

Se guarda el avance por indicación del usuario. Proyecto Firebase `gym-vip-e03-pruebas-qquerar` creado en plan Spark y Android `com.gymvip.mobile.local` registrado; aún sin descarga de configuración, cuenta de servicio ni envío FCM real. La descarga del navegador integrado no se completó y el control de Chrome fue bloqueado al no poder verificar con certeza su URL. No se publican credenciales ni se acredita una integración real terminada.

Suite backend final: 268 pruebas sin fallos y package correcto. Base local respaldada en `gymvip-before-push-v29.dump` fuera de Git antes de aplicar V29. La base de pruebas permanece aislada. Android tuvo 59 pruebas comunes y cuatro instrumentadas correctas antes de los últimos cambios de ruta de pedidos y aviso visual; estos últimos requieren recompilación y repetición de pruebas al retomar. La configuración local de Docker admite las variables Firebase, pero están sin configurar. Se detienen los servicios y el emulador conservando datos y configuración.
