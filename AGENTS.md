# Instrucciones para Agentes — GYM VIP Backend

> **Repositorio:** `gymvip-backend`  
> **Documentación General:** Consulte `../AGENTS.md` y la carpeta `../documentos/` en la raíz de GYMVIP.

## Reglas Clave para este Repositorio:
1. **Rama de Trabajo:** Trabajar en `feature/post-revision-diseno-qa`. Base histórica: `feature/s1-jorge-hu02-hu09-hu11-hu12`.
2. **Arquitectura:** Arquitectura por capas que aplica principios de Clean Architecture (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `config`, `util`, `enums`, `exception`).
3. **Persistencia:** PostgreSQL 17 exclusivo. Migraciones Flyway estrictamente aditivas en `src/main/resources/db/migration/` (V1 a V5 existentes). Hibernate en `ddl-auto: validate`.
4. **Seguridad:** Spring Security 6 stateless con token `Bearer` o `X-Auth-Token`. BCrypt para hashing. Roles: `ADMIN` y `RECEPCION`.
5. **Pruebas:** Los tests de integración usan esquema aislado mediante `TEST_DB_SCHEMA` (`gymvip_test_jorge`). `IsolationGuardTest` impide correr contra `public`.
6. **Comandos:**
   - Pruebas unitarias: `.\mvnw.cmd test "-Dtest=CoveragePolicyTest,MembershipDatesTest,IsolationGuardTest"`
   - Suite completa: `.\mvnw.cmd test`
   - Servidor local: `.\mvnw.cmd spring-boot:run` (puerto 8081).
7. **Sincronización:** Si modificas endpoints, DTOs o reglas de negocio, actualiza en el mismo commit `../documentos/APIDOCUMENTATION.md` y `../documentos/REGLAS.md`.

