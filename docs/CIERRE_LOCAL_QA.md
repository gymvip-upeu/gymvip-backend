# Comprobaciones locales de cierre

Fecha: 24 de noviembre de 2026, America/Lima.

La suite completa ejecutó 145 pruebas sin fallos contra un PostgreSQL 17 aislado en el puerto local 55433, base gymvip_checks_jorge y esquema gymvip_test_jorge. Se mantuvo la protección de esquema de IsolatedDatabaseTestConfig.

```powershell
$env:JAVA_HOME='<ruta al JDK 21 instalado>'
$env:TEST_DB_JDBC_URL='jdbc:postgresql://127.0.0.1:55433/gymvip_checks_jorge'
$env:TEST_DB_SCHEMA='gymvip_test_jorge'
# Configurar TEST_DB_USERNAME y TEST_DB_PASSWORD para la base aislada.
.\mvnw.cmd --batch-mode test
```

JaCoCo genera `target/site/jacoco/index.html` y `jacoco.xml`. No se excluye código propio para incrementar artificialmente la cobertura. El perfil de pruebas no se ejecutó contra una base compartida o de producción.

Se corrigió una prueba que alteraba bits de relleno de Base64URL en vez de alterar necesariamente la firma. JwtService rechaza además codificaciones JWT no canónicas; una regresión específica comprueba esta condición en validación y extracción de sesión.

Hallazgos Sonar corregidos en fuente: literales repetidos de actor de auditoría y cambio obligatorio de contraseña, multiplicación con tipo long y normalización explícita de búsqueda nula en catálogo. El paquete de evidencia conserva antes/después y claves de los hallazgos. El Dockerfile modificado previamente no forma parte de estas correcciones.

El análisis CLI usa las dependencias de `target/sonar-libs`; prepararlas con `mvnw.cmd dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/sonar-libs` después de ejecutar las pruebas. El token del analizador no se almacena en este repositorio.
