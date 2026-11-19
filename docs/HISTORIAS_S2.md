# Historias de usuario del Sprint 2

## Avisos y comunicaciones

Los avisos de la app se guardan con la fecha de vencimiento, el plan y el número de días. Se publican únicamente en la bandeja del socio que autorizó el canal. Cuando WhatsApp está configurado y el número no es válido, se usa la bandeja si el socio ha habilitado el canal. La evaluación manual se mantiene disponible para Administración. El corte diario a las 10 a. m. de Lima se activa en el entorno que corresponda con `APP_REMINDERS_SCHEDULE_ENABLED=true`.

La consulta de comunicaciones conserva los filtros y la paginación cuando el microservicio está caído. En ese caso muestra un aviso de disponibilidad, teléfonos enmascarados y mensajes de error sin detalles internos. Los estados de aceptación del proveedor, envío y entrega tienen significados distintos.

## Catálogo, promociones y FAQ

El catálogo móvil consulta productos publicados del backend, con filtros por nombre, marca, categoría, precio máximo y promoción vigente. Los agotados permanecen publicados y señalados hasta que Administración cambie su estado o los oculte. Las ofertas vigentes muestran su precio, fechas y condiciones. El contacto comercial se realiza por WhatsApp de recepción. La compra dentro de la app queda fuera de esta entrega por decisión del usuario.

Administración publica las respuestas frecuentes y recepción puede presentar cambios para revisión. La app diferencia una consulta fallida de una lista vacía y permite buscar por texto y categoría. Está pendiente aprobar el tratamiento de respuestas automáticas en WhatsApp y verificar la información con el gimnasio.

### Criterios y escenarios de QA para HU-26 y HU-27

- Iniciar sesión con una cuenta de socio habilitada y una membresía vencida. Abrir Catálogo y Promociones vigentes. Confirmar que los productos publicados siguen disponibles para consulta. Una renovación mantiene el acceso de la cuenta habilitada. Desactivar la cuenta desde Administración debe revocar el acceso, incluso con un token anterior.
- Cambiar precio, imagen o disponibilidad desde Administración y volver a abrir el catálogo. Revisar nombre, marca, descripción, presentación, contenido, precio e imagen. Un producto agotado sigue visible con su estado hasta que Administración lo cambie u oculte.
- Combinar nombre o marca con categoría y precio máximo. En Promociones vigentes buscar por nombre del suplemento o de la oferta. Abrir el detalle y comprobar el precio vigente, el ahorro, las fechas en Lima y las condiciones publicadas.
- Tras el fin de la vigencia, consultar la oferta vencida más reciente de cada producto publicado en Finalizadas. El detalle identifica el precio de la oferta como anterior y muestra el precio actual del producto. Las futuras y las desactivadas no aparecen.
- Abrir el contacto de recepción y comprobar la URL de WhatsApp. Probar sin conexión para distinguir el error de una consulta exitosa sin productos u ofertas.

## Verificación

El backend ejecuta sus pruebas contra PostgreSQL 17 en un esquema aislado. El frontend ejecuta la suite de Node y el empaquetado de Vite. El cliente Kotlin Multiplatform ejecuta las pruebas del destino JVM desktop. El microservicio ejecuta las pruebas de destinatarios, permisos, filtros, estados y webhook. Las pruebas de WhatsApp del repositorio utilizan simulación. La aprobación de plantillas y la entrega real deben comprobarse con destinatarios de prueba autorizados en Meta.
