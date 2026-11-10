# Catálogo de consulta para socios — ajuste autorizado

9 de noviembre de 2026. El usuario autorizó expresamente modificar el backend para que CLIENTE consulte el catálogo conservando los permisos de ADMIN y RECEPCION. Rama de trabajo indicada por `AGENTS.md`: `feature/post-revision-diseno-qa`.

## Cambio

`SecurityConfig` permite a ADMIN/RECEPCION/CLIENTE únicamente las lecturas GET de opciones, listado/detalle de suplementos, sus imágenes y listado/detalle de promociones. Los patrones tienen profundidad limitada: no incluyen historiales. No se abrió todo `/catalog/**` ni se cambiaron reglas de otras rutas.

`CatalogController` sigue forzando publicación y el indicador de cliente para CLIENTE; `CatalogService` mantiene ocultos no publicados e imágenes asociadas y limita promociones a vigentes publicadas. Escrituras e historiales conservan `@PreAuthorize("hasRole('ADMIN')")`. Recepción mantiene lectura sin adquirir escritura, y el administrador mantiene su acceso.

Las pruebas detectaron un error previo al omitir el filtro `published`: el ternario `cust ? true : published` intentaba desempaquetar un `Boolean` nulo para personal y devolvía 500. Se usa `Boolean.TRUE` en el ternario para conservar el valor nulo opcional de personal sin ampliar permisos ni cambiar el servicio.

## Matriz

| Ruta bajo `/api/v1/catalog` | ADMIN | RECEPCION | CLIENTE | Anónimo |
|---|---|---|---|---|
| GET options, supplements, supplements/{id}, supplements/{id}/images/{image}, promotions, promotions/{id} | Lectura | Lectura | Lectura publicada; promociones vigentes | 401 |
| POST/PUT supplements, PATCH supplements/{id}/state | Permitido | 403 | 403 | 401 |
| POST/PUT promotions, POST promotions/{id}/deactivate | Permitido | 403 | 403 | 401 |
| GET supplements/{id}/history, promotions/{id}/history | Permitido | 403 | 403 | 401 |

## Verificación y alcance

- `CatalogSecurityTest`: cadena HTTP y anotaciones reales con servicio/repositorios simulados; cubre los tres roles, anónimo, publicaciones, rutas administrativas, escrituras, historiales y filtro opcional de personal.
- `CatalogIntegrationTest`: PostgreSQL real en base `gymvip_checks_jorge`, esquema `gymvip_test_jorge`; comprueba lectura CLIENTE y 404 de producto/imagen ocultos. Actualiza la antigua expectativa de rechazo de CLIENTE.
- La base de pruebas se creó separada de `gymvip_sprint1_jorge`, usando las credenciales locales existentes solo en el proceso. La guarda de pruebas exige el esquema aislado antes de ejecutar Flyway. No se cambiaron usuarios, contraseñas ni roles del gimnasio.
- Resultado final y despliegue local se registran en la evidencia de QA y en la documentación móvil. El commit se publicó para revisión en la rama `codex/123456jor-backend-fase5`; no se desplegó a producción.

Resultado de fase 4: `mvnw.cmd package` terminó BUILD SUCCESS con **142 pruebas, 0 fallos, 0 errores y 0 omitidas**. En fase 5 se añadieron dos regresiones de consentimiento/Avisos y la suite completa cerró con **144/144**. El JAR probado se empaquetó como `gymvip-backend:fase5`; el único backend GYM VIP activo es `gymvip-backend-s3-mobiles-v2` en 8081 y responde `UP`. Las copias de contenedor antiguas se retiraron. PostgreSQL conserva el volumen `gymvip_sprint1_jorge_data`; el puerto local pasó de 55432 a 15432 porque Windows bloqueaba 55432 y 55433 después de reiniciar Docker. La URL JDBC del backend se actualizó al nuevo puerto; los datos no se migraron. El Dockerfile que ya estaba modificado al iniciar la tarea no fue editado. Ver [ajuste de lecturas](PROPUESTA_LECTURAS_SOCIO.md).

Reproducir con JDK 21 y las variables TEST_DB_JDBC_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD y TEST_DB_SCHEMA=gymvip_test_jorge inyectadas al proceso:

```powershell
.\mvnw.cmd package
```

La carpeta canónica `../documentos` no existía en este checkout al iniciar el cambio. Se crean allí documentos de incremento de API y QA según `AGENTS.md`; este documento queda versionable dentro del backend.
