# Ajuste aplicado — error 500 en consentimiento y avisos

Detectado en la fase 5 con app Android y backend real en PostgreSQL aislado. El usuario autorizó explícitamente la corrección el 9 de noviembre de 2026; está aplicada en `MemberWorkspaceService`.

`MemberWorkspaceService.getConsents` y `listInbox` están declarados `@Transactional(readOnly=true)` y llaman `lockClient`, que ejecuta `SELECT ... FOR UPDATE`. PostgreSQL rechaza ese bloqueo dentro de una transacción de solo lectura. La consulta devuelve 500: impide confirmar el consentimiento antes de activar y cargar Avisos. La app conserva el error y no simula éxito.

Cambio mínimo aplicado, solo en esos dos métodos:

```diff
 @Transactional(readOnly = true)
 public ConsentStatus getConsents(UUID clientId) {
-    lockClient(clientId);
+    clients.getClientById(clientId);
```

```diff
 @Transactional(readOnly = true)
 public List<MemberInboxItem> listInbox(UUID clientId) {
-    lockClient(clientId);
+    clients.getClientById(clientId);
```

Se reutiliza la comprobación de existencia de lectura que ya emplean workspace e historial de asistencias. Se conservan los bloqueos de las escrituras, el cambio obligatorio de contraseña, `ownClient` y los permisos CLIENTE del controlador. No cambia esquema, DTO, contraseña ni rol de ningún usuario.

Validación: regresión PostgreSQL con GET/POST/GET de consentimientos y GET/POST leído/GET de avisos; 403 para personal y cuenta ajena. La activación y el recorrido completos de un socio ficticio se verificaron desde el emulador contra la API real y la base aislada de QA. `mvnw.cmd package`: **144 pruebas, 0 fallos, 0 errores, 0 omitidas**. La app: **99 pruebas, 0 fallos, 0 omitidas**. La imagen local final es `gymvip-backend:fase5` y `/api/v1/health` responde `UP` en 8081.
