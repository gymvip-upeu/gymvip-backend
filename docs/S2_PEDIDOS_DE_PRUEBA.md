# Compras de suplementos con recojo en recepción

La app permite iniciar una compra de prueba desde el detalle de un producto disponible. El comprador revisa el precio vigente en soles, crea el pedido y abre Checkout Pro de Mercado Pago Perú. En **Mis pedidos** puede consultar el estado cuando vuelve a la app.

`GET /api/v1/member-app/store/orders` devuelve hasta 25 pedidos propios y el indicador de disponibilidad del pago de prueba. `POST /orders` recibe `requestId`, `productId`, `quantity` y `expectedUnitPricePen`. Un precio o estado de publicación desactualizado devuelve 409. `POST /orders/{id}/refresh` vuelve a verificar el estado de un pedido propio con el proveedor. El backend exige una cuenta de socio activa, aplica el precio del catálogo y usa la referencia interna como clave de idempotencia de Mercado Pago.

En un entorno de QA, configurar `APP_STORE_MERCADO_PAGO_SANDBOX_ENABLED=true` y `APP_STORE_MERCADO_PAGO_SANDBOX_TOKEN` con el Access Token de prueba del vendedor peruano. Utilizar una cuenta compradora de prueba. El backend mantiene el token en el servidor y valida que la orden devuelta sea de prueba, su referencia corresponda al pedido y su moneda sea PEN. El estado **Pago de prueba confirmado** requiere el monto completo acreditado.

El pedido de prueba usa una unidad desde la app. Sirve para validar el flujo de recojo con recepción. No realiza una entrega comercial ni modifica el inventario real. Para un lanzamiento comercial se deben definir y probar reserva de inventario, conciliación por webhook, verificación en recepción, devoluciones y credenciales del entorno comercial.

[API de órdenes para Checkout Pro](https://www.mercadopago.com.pe/developers/es/docs/checkout-pro-orders/create-order) · [Cuentas de prueba](https://www.mercadopago.com.pe/developers/es/docs/checkout-pro-preferences/test-accounts)
