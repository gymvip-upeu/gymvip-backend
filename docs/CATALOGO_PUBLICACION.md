# Publicación del catálogo y carrito — 25/11/2026

El catálogo del socio filtra published=true independientemente de que tenga una membresía. Agotado permanece consultable; oculto devuelve 404 en ficha e imagen para CLIENTE. La consulta móvil conserva precio, presentación, descripción, ingredientes e imagen del registro administrativo.

Se refuerza updateCartItem: cantidades positivas requieren suplemento publicado y DISPONIBLE; de otro modo responde 409 Conflict con solicitud de actualizar catálogo. Quitar sigue permitido. Esta validación no reserva ni inventa stock; checkout mantiene el control de existencias y la reserva ya implementada.

CatalogIntegrationTest recorre registro oculto, publicación, imagen, edición, agotado, repetición sin duplicados y ocultación para un socio registrado sin membresía. CartJdbcIntegrationTest comprueba rechazo de agotados/ocultos, retirada y separación del stock. Fixtures en PostgreSQL de pruebas, sin envíos de correo y con rollback para los casos nuevos. Suite completa: 283 pruebas correctas.
