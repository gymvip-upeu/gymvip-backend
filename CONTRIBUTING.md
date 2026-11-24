# Guía de Contribución y Estándar de Ingeniería — GYM VIP / VIP CENTER FIT

Esta guía establece las directivas obligatorias para todo el equipo de desarrollo, agentes y colaboradores que interactúan con el ecosistema de software de GYM VIP.

---

## 1. Regla Permanente de Mensajes de Commit

A partir del 23 de Noviembre de 2026, **todos los commits nuevos** deben seguir obligatoriamente este formato exacto:

```text
feature - descripción breve
fix - descripción breve
docs - descripción breve
```

### Reglas Específicas por Repositorio:
* **Backend (`gymvip-backend`):** Mencionar el recurso, servicio o API afectada.
  * Ejemplos: `feature - API metricas dashboard`, `fix - API asistencia`, `fix - webhook Culqi`, `docs - catalogo de APIs`.
* **Frontend (`gymvip-frontend`):** Mencionar el módulo o componente visual.
  * Ejemplos: `feature - graficos dashboard`, `feature - catalogo suplementos`, `fix - filtros asistencia`, `docs - README frontend`.
* **Mobile (`gymvip-mobile`):** Mencionar pantalla o funcionalidad móvil.
  * Ejemplos: `feature - carrito movil`, `feature - acceso biometrico`, `fix - sesion movil`, `docs - generacion APK`.
* **Comunicaciones (`gymvip-ms-comunicaciones`):** Mencionar canal o integración.
  * Ejemplos: `feature - API estado de canales`, `fix - webhook WhatsApp`, `docs - integracion Meta`.
* **Landing Page (`gymvip-Landing-Page` / `gymvip-publico`):** Mencionar página o sección.
  * Ejemplos: `feature - descarga APK`, `fix - metadatos APK`, `docs - despliegue landing`.

### Normas Generales de Commits:
1. Mensajes claros y concisos, preferiblemente menores a 72 caracteres.
2. Un único propósito coherente por commit.
3. Separar siempre cambios de documentación de cambios de código o funcionalidad.
4. Prohibido usar mensajes genéricos (`update`, `changes`, `fix`, `test`).
5. Prohibido incluir credenciales, secretos, tokens o información confidencial.
6. Prohibido alterar o reescribir historial previo.

---

## 2. Política Estricta de Ramas

1. **Rama de Trabajo y Despliegue:** `ACOSTA-MEJORAS-Y-ACTUALIZACIONES` (sincronizada en los cinco repositorios).
2. **Prohibiciones Absolutas:**
   * NUNCA hacer commit directo ni merge hacia `main`.
   * NUNCA hacer force push (`git push -f`).
   * NUNCA eliminar ramas de trabajo de compañeros.
   * NUNCA realizar operaciones destructivas (`git reset --hard`) sobre trabajo no consolidado.

---

## 3. Seguridad y Protección de Credenciales

* PROHIBIDO comitear archivos `.env` o credenciales de producción.
* Utilizar plantillas seguras `.env.example` con valores de ejemplo (placeholders).
* En Frontend (Vite) nunca incluir contraseñas de BD ni claves privadas en variables públicas `VITE_*`.
* Toda clave privada de pasarelas de pago (Culqi) y APIs de mensajería (Meta) debe residir exclusivamente en variables de entorno del servidor Backend.

---

## 4. Definition of Done (DoD)

Un cambio o funcionalidad solo se considera terminado cuando:
1. Se inspeccionó el requisito real.
2. Se implementó el código siguiendo la arquitectura limpia y modular.
3. Se superaron las pruebas unitarias y de integración sin errores.
4. Se verificó que no existan regresiones de seguridad ni de contratos API.
5. Se actualizó la documentación canónica correspondiente en `documentos/`.
6. Se generó el commit con el formato solicitado.
