## Corrección de carrito y fechas JDBC UTC — 25/11/2026

El error 500 al crear member_carts se debía a parámetros java.time.Instant sin tipo JDBC soportado. SupplementOrderService convierte ahora los instantes a LocalDateTime UTC al escribir sus columnas TIMESTAMP WITHOUT TIME ZONE y al consultar vencimientos. Las lecturas convierten explícitamente desde UTC, para conservar la duración real de las reservas. No cambian los DTO, precios, permisos, stock ni la regla de reserva de 30 minutos; no hay migración ni borrado de datos.

La integración PostgreSQL agregó cobertura real para crear/reutilizar/actualizar/vaciar el carrito y para crear un pedido con vencimiento correcto, incluido el cálculo de reservas expiradas. Esa prueba detectó una lectura de ResultSet ya cerrado al devolver el detalle de pedido: se construye ahora el DTO dentro del RowMapper mientras la consulta sigue abierta. Los fixtures unitarios expresan timestamps UTC y los mocks ejecutan el mapeador real.

La app muestra un mensaje específico de carrito/pedidos con Reintentar carrito y estado Actualizando mientras existe una solicitud. Al sincronizar un socio se vuelve a consultar carrito/pedidos y se limpia el error anterior. La consulta no se añade para personal administrativo.

Verificación final 25/11/2026: 280 pruebas backend y 72 móviles correctas, empaquetados correctos. Backend y app del emulador actualizados. En la cuenta de prueba se consultó carrito vacío sin 500, se agregó un suplemento desde el detalle, se abrió el carrito con una unidad y se retiró esa unidad, regresando al catálogo con cero productos y sin mensaje de error. No se confirmó ningún pedido ni se realizó pago. Cambios locales sin publicar.
