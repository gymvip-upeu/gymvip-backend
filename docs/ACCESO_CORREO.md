# Acceso y recuperación de cuentas por correo

Versión de pruebas compartida: `feature/e03-acceso-recuperacion-correo-qquerar`.

Usar la misma rama en los cuatro repositorios del equipo: gymvip-backend, gymvip-ms-comunicaciones, gymvip-frontend y gymvip-mobile. No es un despliegue a producción. No incluye bases de datos, correos privados, contraseñas reales, claves Resend, archivos Firebase privados ni APK compilados.

## Recorrido implementado

- Recepción registra un socio con correo real y genera acceso. El usuario de acceso es su correo normalizado y único.
- Se crea una cuenta pendiente con contraseña temporal aleatoria e invitación, ambas válidas durante 24 horas. El correo incluye las credenciales y el botón Activar mi cuenta.
- Alternativa A: el socio abre el enlace del correo y crea su contraseña desde /activar-cuenta.
- Alternativa B: ingresa en Android con la temporal y ve obligatoriamente Nueva contraseña y Confirmar nueva contraseña antes de Inicio. La sesión temporal autenticada autoriza ese cambio sin pedir de nuevo la temporal.
- Al guardar se invalida la temporal, se consume la invitación y se cierran las sesiones. Se requiere volver a ingresar con la contraseña propia.
- Desde Ajustes → Cuenta y seguridad → Cambiar contraseña se muestra el correo registrado como solo lectura. El backend toma el destinatario de la sesión e ignora cualquier correo que se intente sustituir en la solicitud.
- Se envía un enlace de recuperación aleatorio de 15 minutos y un solo uso. La página /restablecer-contrasena pide nueva contraseña y confirmación; si el enlace falta, venció o ya se usó permite solicitar otro.
- El restablecimiento invalida todos los enlaces de recuperación y sesiones. La app revalida cada 15 segundos mientras está autenticada y vuelve al login al recibir 401; una falla de red no cierra la sesión. El correo de confirmación nunca incluye contraseñas.
- Un socio sin membresía muestra Sin Plan. Los valores nulos de vigencia ya no se interpretan como falta de internet.

## Configuración por desarrollador

Requisitos: PostgreSQL 17, Java 21, Node/npm y Android SDK/JDK del proyecto. Seguir también los README y plantillas .env.example de cada repositorio. Mantener una base local propia; las migraciones Flyway son aditivas, incluida V30. No copiar una base de producción para probar.

Orden: base de datos → comunicaciones (8085) → backend (8081) → web (5173) → Android.

Backend: además de su base de datos, JWT y credenciales locales del personal, configurar:

```dotenv
APP_COMMS_URL=http://127.0.0.1:8085
APP_COMMS_TOKEN=GENERAR_TOKEN_PRIVADO_COMPARTIDO_CON_COMUNICACIONES
MEMBER_ACCESS_ACTIVATION_URL=http://127.0.0.1:5173/activar-cuenta
PASSWORD_RESET_BASE_URL=http://127.0.0.1:5173/restablecer-contrasena
```

Comunicaciones: configurar sus variables en el entorno del proceso o mediante env_file en Docker. El servicio no carga automáticamente un archivo .env al ejecutar Java. Mantener sus variables PostgreSQL y WhatsApp de la plantilla original y agregar:

```dotenv
COMMS_SERVICE_TOKEN=EL_MISMO_TOKEN_PRIVADO_DEL_BACKEND
EMAIL_MODE=production
EMAIL_PROVIDER=resend
EMAIL_FROM=VIP CENTER FIT <onboarding@resend.dev>
RESEND_API_KEY=
```

Introducir la clave real únicamente en la configuración privada. No usar VITE_* ni guardar la clave en la app, documentos o Git. EMAIL_MODE es independiente de COMMS_MODE: probar correo real no activa WhatsApp real.

Sin dominio verificado, Resend solo permite enviar desde resend.dev al correo asociado a la cuenta Resend del desarrollador. Cada compañero puede crear su cuenta/clave y registrar un socio con ese mismo correo en su base propia. Para enviar a otros socios verificar un dominio propio y cambiar EMAIL_FROM. Referencia oficial: https://resend.com/docs/knowledge-base/403-error-resend-dev-domain

La web usa su plantilla .env.example para apuntar al backend local. Si se usa Vite proxy relativo, abrir web y correo con el mismo origen 127.0.0.1:5173. No agregar secretos en variables VITE_*.

## Android físico y emulador

Emulador: compilar con -Pgymvip.backendUrl=http://10.0.2.2:8081. Teléfono USB: compilar con -Pgymvip.backendUrl=http://127.0.0.1:8081. No intercambiar ambas APK sin revisar su URL.

Desde el repositorio móvil, para USB:

```powershell
.\gradlew.bat :composeApp:assembleDebug '-Pgymvip.firebase=false' '-Pgymvip.backendUrl=http://127.0.0.1:8081' '-Pgymvip.debugApplicationId=com.gymvip.mobile.local'
.\scripts\probar-android-fisico-usb.ps1
```

El script selecciona un teléfono autorizado, comprueba backend y web, redirige por USB los puertos 8081 y 5173, instala sin borrar datos y abre la app. Si hay varios teléfonos indicar -DeviceSerial. Primero activar Depuración USB y autorizar la computadora. Mantener cable y servidores encendidos. Las URL localhost del correo funcionan en el teléfono solo con esta redirección; fuera del equipo se requiere una URL accesible HTTPS.

Firebase real sigue pendiente de configuración. Los correos y la bandeja de Avisos no dependen de Firebase; las notificaciones push no se incluyen como validadas en esta entrega.

## Validación y límites

Validado manualmente por el usuario: llegada de invitación a Gmail; primer acceso temporal desde app y creación obligatoria; ingreso a Inicio; recuperación desde Ajustes, creación de contraseña y nuevo ingreso; confirmación sin contraseñas; rechazo de contraseña anterior y enlace ya utilizado. Se comprobó el envío Delivered en Resend y la apertura del formulario público de recuperación sin sesión administrativa.

Pruebas automatizadas cubren token vencido/reutilizado, rotación, sesiones revocadas, fallo de envío y reintento, destinatario ligado a sesión y acceso restringido hasta crear contraseña. Evidencias de ejecución se conservan en el espacio local; no subir logs con datos sensibles. La suite backend usa una base de pruebas aislada (TEST_DB_JDBC_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD, TEST_DB_SCHEMA); nunca apuntarla a public ni a la base compartida del equipo.

Pendiente: repetir manualmente la alternativa Activar mi cuenta con un socio nuevo; validar en teléfono físico conectado; verificar dominio para distintos destinatarios; publicar enlaces HTTPS para uso general y completar push real.

Seguridad: hashes BCrypt de contraseñas y SHA-256 de tokens, enlaces en fragmento retirado del historial, contraseña propia de mínimo 12 caracteres con mayúscula/minúscula/número y máximo 72 bytes UTF-8. Estados QUEUED, ACCEPTED, SIMULATED, FAILED y TEST_DISABLED: ACCEPTED solo confirma aceptación del proveedor, no lectura/entrega. La interfaz no ofrece una simulación como correo real.

Límites operativos de pruebas: invitaciones se despachan después del commit en una cola en memoria; si el proceso se reinicia antes de enviarlas hay que revisar el estado y reenviar. Límites de solicitudes en memoria por instancia. No hay garantía de entrega ni cola persistente de correo; estas mejoras se evalúan antes del uso general. Reenvío de invitación: mínimo un minuto, máximo tres emisiones/hora. Recuperación: espera de un minuto en solicitudes autenticadas y máximo tres peticiones por IP/correo cada 15 minutos.

Verificación de esta versión antes de publicar: backend 278 pruebas; comunicaciones 37; móvil 66 y APK debug; web 100, lint y build. Todas correctas.
