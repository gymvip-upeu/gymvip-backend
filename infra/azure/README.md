# Despliegue GYM VIP en Azure

La infraestructura se aplica por ambiente. El workflow manual en `develop` ejecuta un what-if por defecto. GitHub debe tener ambientes protegidos `staging` y `production`, federación OIDC por ambiente y revisión obligatoria de producción.

## Prerrequisitos del ambiente

- Grupo de recursos y región con cuota. Usar una VNet con subredes separadas para Container Apps, PostgreSQL y monitorización, y DNS privado vinculado. El entorno Container Apps debe estar configurado como interno. Validar esta propiedad antes de ejecutar la plantilla de aplicaciones.
- Azure Container Registry y una identidad administrada con permiso de lectura de imágenes y secretos. Las imágenes de los dos servicios Java se construyen desde los commits aprobados y se referencian mediante digest.
- Key Vault con secretos diferentes por ambiente. `coreSecretUrls` y `commsSecretUrls` reciben objetos con nombres de variables y URI de secretos. Los valores de los secretos se inyectan en ejecución y se excluyen del repositorio y de los parámetros del workflow.
- Las entradas públicas llegan por un gateway HTTPS que expone solo rutas de negocio de la API y el webhook de comunicaciones. Restringir `/actuator/**` a la red interna y comprobarlo desde fuera. Configurar CORS con los orígenes exactos del portal y la landing.

## Secuencia de staging

1. Revisar el costo, verificar las subredes existentes y compilar `database.bicep`, `monitoring.bicep` y `main.bicep`. Ejecutar `az deployment group what-if` sobre cada plantilla con los parámetros del ambiente.
2. Aprovisionar PostgreSQL privado. Crear un rol propietario y una credencial distinta por cada base. Core usa `gymvip_core` y comunicaciones `gymvip_comms`. Dar a cada servicio acceso solo a su base. Revocar accesos públicos de conexión, habilitar TLS y guardar las credenciales en Key Vault.
3. Desplegar ambos servicios en modo de prueba. Para el backend configurar JWT, la URL JDBC, el usuario y el secreto de conexión, token de comunicaciones y token del scrape. Para comunicaciones configurar JDBC y token de servicio, y otro token de scrape. Validar readiness y Flyway. Mantener Culqi live desactivado y comunicaciones en simulación durante este paso.
4. Publicar los builds estáticos desde `develop` en los dos recursos Static Web Apps. El portal necesita `VITE_API_BASE_URL` y la URL HTTPS de Grafana si está accesible por operadores. La landing necesita `PUBLIC_API_BASE_URL`, `PUBLIC_SITE_URL` y `PUBLIC_ADMIN_URL`. Configurar fallback a `/index.html` exclusivamente en el portal SPA.
5. En la VM privada, montar el disco de datos para Docker y copiar los archivos de `infra/monitoring`. Crear `prometheus.yml` desde el ejemplo con los FQDN internos. Cargar en `/etc/gymvip/metrics/` el token de cada servicio y la contraseña inicial de Grafana desde el almacén de secretos, con permisos de lectura para sus procesos. Usar imágenes de Prometheus y Grafana aprobadas por digest y ejecutar `docker compose config --quiet` seguido de `docker compose up -d`. Grafana escucha en loopback para acceso por túnel privado. Para habilitar el enlace web, configurar un proxy HTTPS de operadores con autenticación.
6. Probar una solicitud de alta empresarial, reportes por empresa y sede, cobro de prueba y webhook repetido, QR, entrega de WhatsApp y Resend y Android físico con FCM. Para pagos o comunicaciones reales, habilitar canales en el ambiente solo después de demostrar el recorrido de prueba con las credenciales autorizadas. Comprobar un scrape correcto y rechazo sin token, una alarma por fallo controlado y una restauración en otra base privada.

## Producción y reversión

Registrar la revisión activa de cada Container App, artefactos de ambos sitios, versión APK y evidencia de restauración antes del corte. En caso de fallo revertir tráfico y artefactos a las versiones aprobadas. Tratar una migración ya aplicada como cambio de datos y ensayar cualquier restauración en una copia antes de modificar producción. Documentar el punto de recuperación y la ventana estimada con el volumen real.

La preparación local no aprovisiona recursos ni acredita la configuración de red, el presupuesto o la entrega de proveedores.
