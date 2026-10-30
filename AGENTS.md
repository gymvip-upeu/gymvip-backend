# Instrucciones para Agentes y Desarrolladores — GYM VIP Backend

> **Repositorio:** `gymvip-backend`  
> **Documentación Canónica Centralizada:** Ubicada en `../gymvip-frontend/documentos/`

---

## 1. Reglas Clave para este Repositorio:
1. **Rama de Trabajo:** Trabajar en la rama local `feature/ajustes-diseno-documentacion-seguridad`. NUNCA modificar directamente `main`, `develop` ni ramas individuales de compañeros.
2. **Arquitectura:** Arquitectura por capas que aplica principios de Clean Architecture (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `config`, `util`, `enums`, `exception`). Prefijo autoritativo de rutas: `/api/v1`.
3. **Persistencia y Base de Datos:** PostgreSQL 17 exclusivo. Migraciones Flyway estrictamente aditivas en `src/main/resources/db/migration/` (V1 a V5 existentes). Hibernate configurado en `ddl-auto: validate`.
4. **Seguridad:** Spring Security 6 stateless con token opaco UUID (`Bearer` o `X-Auth-Token`). BCrypt para contraseñas. Control de acceso RBAC estricto (`ADMIN` y `RECEPCION`) con `@PreAuthorize`.
5. **Pruebas Automatizadas:**
   - Pruebas unitarias de dominio: `.\mvnw.cmd test "-Dtest=CoveragePolicyTest,MembershipDatesTest,IsolationGuardTest"`
   - Suite de integración completa: `.\mvnw.cmd test` (requiere base de datos PostgreSQL local activa con esquema `TEST_DB_SCHEMA`).
6. **Comando de Servidor Local:** `.\mvnw.cmd spring-boot:run` (puerto 8081).

---

## 2. Regla de Documentación Viva por Cambio Significativo

Todo cambio que altere contratos, base de datos o lógica debe sincronizar el documento canónico en `../gymvip-frontend/documentos/`:
- Si cambia un endpoint, DTO o header: [`../gymvip-frontend/documentos/APIDOCUMENTATION.md`](../gymvip-frontend/documentos/APIDOCUMENTATION.md).
- Si cambia esquema, entidad o arquitectura: [`../gymvip-frontend/documentos/ARQUITECTURA_Y_MODELOS.md`](../gymvip-frontend/documentos/ARQUITECTURA_Y_MODELOS.md).
- Si se ejecutan pruebas o se evalúa seguridad: [`../gymvip-frontend/documentos/EVIDENCIAS_QA_SEGURIDAD.md`](../gymvip-frontend/documentos/EVIDENCIAS_QA_SEGURIDAD.md).
- Si cambia una regla de negocio o DoD: [`../gymvip-frontend/documentos/REGLAS.md`](../gymvip-frontend/documentos/REGLAS.md).
- Si cambian dependencias o perfiles: [`../gymvip-frontend/documentos/FICHA_TECNICA.md`](../gymvip-frontend/documentos/FICHA_TECNICA.md).
