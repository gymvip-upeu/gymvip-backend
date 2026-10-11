# Definición de Terminado (Definition of Done — DoD)

**Proyecto:** GYM VIP (VIP CENTER FIT)  
**Alcance:** Aplica a toda Historia de Usuario e incremento entregable.  
**Regla fundamental:** Un mock visual, un prototipo no integrado o un endpoint sin persistencia verificada **NO** acreditan el cierre de una Historia de Usuario.

---

## Criterios Obligatorios de Aceptación y Cierre

### 1. Código Fuente y Arquitectura
- [ ] Implementación distribuida en las capas correspondientes de Clean Architecture: `domain`, `application`, `infrastructure`, `api`.
- [ ] Sin lógica de negocio acoplada en controladores ni en componentes React de presentación.
- [ ] Reutilización de DTOs tipados, contratos inmutables y validaciones semánticas (`@NotBlank`, `@Positive`, etc.).
- [ ] Ausencia total de credenciales, contraseñas, tokens JWT secretos o cadenas de conexión hardcodeadas en código fuente.

### 2. Base de Datos y Persistencia
- [ ] Esquema de base de datos migrado formalmente mediante scripts SQL versionados con restricciones de clave primaria, unicidad e integridad referencial.
- [ ] Persistencia real verificada: los registros creados a través de la API o la interfaz web se leen con éxito tras detener y reiniciar el servidor backend.
- [ ] Prohibición estricta de borrado físico (`DELETE`) en entidades auditables, contratos, clientes y registros de pagos; las correcciones o anulaciones se modelan como nuevas operaciones con estado o asientos compensatorios.

### 3. Seguridad y Control de Acceso
- [ ] Autenticación de usuarios activa; sesiones o tokens validados contra el contexto de seguridad.
- [ ] Autorización estricta por roles a nivel de endpoint backend (HTTP 401 para no autenticados, HTTP 403 para roles insuficientes).
- [ ] Contraseñas personales almacenadas exclusivamente con algoritmos de hashing seguros (BCrypt / Argon2 con salt aleatorio).
- [ ] Generación automática de eventos de auditoría (`AuditEvent`) registrando actor, acción, entidad impactada, instante UTC, zona horaria e IP/canal.

### 4. Calidad y Pruebas
- [ ] Pruebas unitarias de las reglas de dominio pasando exitosamente.
- [ ] Pruebas de integración para casos de éxito y casos límite o de error (400 Bad Request, 403 Forbidden, 404 Not Found, 409 Conflict).
- [ ] Compilación limpia del backend (`mvn clean test` o `mvn clean package`) sin errores ni fallos de pruebas.
- [ ] Compilación limpia del frontend (`npm run build`) sin errores de tipos o sintaxis.

### 5. Interfaz de Usuario (UI)
- [ ] Interfaz conectada a la API real, consumiendo y enviando datos hacia los endpoints correspondientes.
- [ ] Implementación rigurosa de los 4 estados visuales:
  1. **Carga (`Loading`):** Indicador visual de espera o esqueleto.
  2. **Vacío (`Empty`):** Mensaje explicativo cuando no existen registros.
  3. **Error (`Error`):** Notificación comprensible con causa y opción de reintento.
  4. **Éxito (`Success`):** Confirmación clara de la operación realizada.
- [ ] Cumplimiento de tokens de color, tipografía y espaciado de `DESIGN_SYSTEM.md`.
- [ ] Soporte de navegación por teclado, accesibilidad y respeto de preferencia `prefers-reduced-motion`.

### 6. Documentación y Control de Versiones
- [ ] Documentación técnica y contratos OpenAPI / Markdown actualizados.
- [ ] Commits locales pequeños, atómicos y descriptivos en Git, cubriendo únicamente cambios probados y coherentes.
- [ ] Registro transparente de cualquier decisión de negocio que permanezca en estado `PENDIENTE` para validación con el cliente.
