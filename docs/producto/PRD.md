# PRD Canónico — GYM VIP (VIP CENTER FIT)

**Versión de entrega:** 1.0 — 11 de octubre de 2026  
**Estado:** Documento Canónico de Producto (insumo para revisión de VIP CENTER FIT y equipo de desarrollo)  
**Fuentes oficiales:** Product Backlog GYM VIP V1.0.xlsx (HU-01 a HU-34), Levantamiento y definición del Product Backlog.docx, Project Charter Ágil VIP CENTER FIT (UPeU, RUC 20611261340), PRD preliminar.  
*Nota metodológica:* Los Sprints antiguos del Charter (fechas históricas de 2023/2026 no consensuadas) no se utilizan como plan vigente. La reunión del lunes validará las decisiones marcadas como `PENDIENTE`.

---

## 1. Problema de Negocio, Objetivo y Alcance

### 1.1 Contexto y Diagnóstico
VIP CENTER FIT S.A.C. (RUC 20611261340) es un centro de acondicionamiento físico con una base estimada de 120 clientes activos, 30–35 renovaciones semanales y aproximadamente 10 consultas diarias en recepción. Actualmente experimenta fricción operativa por controles manuales o dispersos, lo que ha generado entre 3 y 5 casos mensuales de socios que asisten con membresías vencidas sin detección oportuna, además de sobrecarga en recepción para resolver dudas sobre vencimientos, pagos y rutinas.

### 1.2 Objetivo del Producto
Centralizar la gestión integral de socios, membresías y pagos presenciales en una plataforma confiable, garantizando:
1. **Identidad única y trazable:** Ficha de socio protegida contra duplicados de documentos y con historial inalterable.
2. **Ciclo de membresías transparente:** Configuración de planes versionados, contratación presencial transaccional y cálculo exacto de vigencias.
3. **Autoservicio para el socio:** Consulta móvil de su membresía y pagos en modo solo lectura, incluso con membresía vencida, evitando filas en recepción.
4. **Comunicaciones automatizadas:** Un único microservicio desacoplado para recordatorios y avisos vía WhatsApp/correo.

### 1.3 Delimitación y Exclusiones de Alcance (MVP y V1)
- **INCLUIDO:** Web administrativa (React) para recepción y administración; Backend de negocio modular (Spring Boot con PostgreSQL / almacenamiento persistente); Microservicio único de comunicaciones con stubs de prueba; Registro de pagos presenciales (efectivo, Yape/Plin, transferencia, POS); Aplicación móvil de consulta para el socio (Kotlin Multiplatform).
- **EXCLUIDO EXPLÍCITAMENTE:**
  - Pasarela de pagos por internet o almacenamiento de tarjetas de crédito/débito.
  - Integración electrónica con torniquetes o molinetes físicos.
  - Captura o almacenamiento de huellas dactilares o fotografías biométricas.
  - Facturación electrónica / comprobantes fiscales SUNAT automáticos.
  - Sincronización offline distribuida bidireccional completa.
  - Rol de Entrenador (el Charter mencionaba rutinas, pero la nueva propuesta de seguridad prescinde de este rol; ver decisiones pendientes).

---

## 2. Índice Canónico de 6 Épicas y 34 Historias de Usuario (HU-01 a HU-34)

*Los textos, IDs, prioridades iniciales MoSCoW y plataformas corresponden fielmente a `Product Backlog GYM VIP V1.0.xlsx`.*

| ID | Épica | Nombre de Historia | Historia de Usuario | Prioridad | Plataforma | Estado | Dependencias |
|---|---|---|---|---|---|---|---|
| **HU-01** | E01 · Gestión de usuarios y clientes | Registrar clientes | Como administrador, quiero registrar los datos de los clientes para mantener un control organizado de las personas inscritas en el gimnasio. | Must Have | WEB | Refinada P0 | — |
| **HU-02** | E01 · Gestión de usuarios y clientes | Consultar y actualizar clientes | Como personal autorizado, quiero consultar y actualizar los datos de los clientes para mantener su información vigente. | Must Have | WEB | Refinada P0/P1 | HU-01 |
| **HU-03** | E01 · Gestión de usuarios y clientes | Inicio de sesión | Como usuario autorizado, quiero iniciar sesión mediante mis credenciales para acceder de forma segura al sistema según mi rol. | Must Have | AMBOS | Refinada P0 | — |
| **HU-04** | E01 · Gestión de usuarios y clientes | Roles y permisos | Como administrador, quiero gestionar los roles y permisos del personal para controlar el acceso a las funcionalidades de la plataforma. | Must Have | WEB | Refinada P0/P1 | HU-03 |
| **HU-05** | E01 · Gestión de usuarios y clientes | Habilitar acceso móvil | Como administrador, quiero habilitar el acceso móvil de un cliente registrado para que pueda utilizar los servicios digitales de GYM VIP. | Must Have | AMBOS | Refinada P1 | HU-01, HU-03 |
| **HU-06** | E02 · Membresías, pagos y renovaciones | Configurar planes | Como administrador, quiero registrar y configurar los planes de membresía con sus precios, duración, condiciones y beneficios para informar correctamente los servicios que ofrece el gimnasio. | Must Have | WEB | Refinada P0/P1 | HU-03, HU-04 |
| **HU-07** | E02 · Membresías, pagos y renovaciones | Asignar membresías | Como personal de recepción, quiero asignar una membresía a un cliente para registrar el servicio que ha contratado. | Must Have | WEB | Refinada P1 | HU-01, HU-06 |
| **HU-08** | E02 · Membresías, pagos y renovaciones | Registrar pagos | Como personal de recepción, quiero registrar los pagos de las membresías para mantener actualizado el historial de las operaciones realizadas. | Must Have | WEB | Refinada P1 | HU-07 |
| **HU-09** | E02 · Membresías, pagos y renovaciones | Controlar vigencias | Como administrador, quiero consultar las fechas de inicio, vencimiento y estado de las membresías para identificar cuáles se encuentran vigentes, próximas a vencer o vencidas. | Must Have | WEB / API | Refinada P1 (Condicionada) | HU-06, HU-07 |
| **HU-10** | E02 · Membresías, pagos y renovaciones | Renovar membresías | Como personal de recepción, quiero renovar la membresía de un cliente para registrar correctamente su nuevo periodo de vigencia. | Must Have | WEB | Refinada P2 (Condicionada) | HU-07, HU-09 |
| **HU-11** | E02 · Membresías, pagos y renovaciones | Listado de vencimientos | Como administrador, quiero visualizar un listado de membresías próximas a vencer y vencidas para realizar un seguimiento oportuno de los clientes. | Must Have | WEB | Refinada P1 | HU-09 |
| **HU-12** | E02 · Membresías, pagos y renovaciones | Verificar membresía | Como personal autorizado, quiero verificar el estado de la membresía de un cliente para identificar posibles situaciones de asistencia con una membresía vencida. | Must Have | WEB | Refinada P1 | HU-09 |
| **HU-13** | E03 · Atención, comunicación y notificaciones | Configurar recordatorios | Como administrador, quiero configurar recordatorios automáticos de vencimiento de membresías para reducir la dependencia del seguimiento manual. | Must Have | WEB / MICRO | Propuesto | HU-09 |
| **HU-14** | E03 · Atención, comunicación y notificaciones | Recibir avisos | Como cliente, quiero recibir avisos relacionados con el estado de mi membresía para mantenerme informado sobre su próxima renovación. | Must Have | APP / MICRO | Propuesto | HU-13, HU-28 |
| **HU-15** | E03 · Atención, comunicación y notificaciones | Gestionar respuestas frecuentes | Como administrador, quiero gestionar mensajes de respuesta a consultas frecuentes para agilizar la atención de los clientes. | Should Have | WEB / MICRO | Propuesto | HU-03 |
| **HU-16** | E03 · Atención, comunicación y notificaciones | Comunicaciones WhatsApp | Como administrador, quiero utilizar la integración de comunicaciones con WhatsApp para automatizar los mensajes contemplados por el gimnasio. | Must Have | MICRO | Propuesto | HU-13 |
| **HU-17** | E03 · Atención, comunicación y notificaciones | Estado de comunicaciones | Como personal autorizado, quiero consultar el estado de los envíos de comunicaciones para identificar mensajes enviados correctamente o intentos fallidos. | Should Have | WEB / MICRO | Propuesto | HU-13 |
| **HU-18** | E04 · Servicios y seguimiento del entrenamiento | Registrar rutinas | Como entrenador, quiero registrar rutinas de entrenamiento para organizar las actividades asignadas a los clientes. | Must Have | WEB | Pendiente de rol | HU-01, HU-04 |
| **HU-19** | E04 · Servicios y seguimiento del entrenamiento | Asignar y actualizar rutinas | Como entrenador, quiero asignar y actualizar rutinas para mantener vigente la planificación de entrenamiento de cada cliente. | Must Have | WEB | Pendiente de rol | HU-18 |
| **HU-20** | E04 · Servicios y seguimiento del entrenamiento | Consultar seguimiento | Como entrenador, quiero consultar la información relacionada con las rutinas y los servicios asignados para realizar el seguimiento correspondiente. | Should Have | WEB | Pendiente de rol | HU-19 |
| **HU-21** | E04 · Servicios y seguimiento del entrenamiento | Registrar servicios | Como administrador, quiero registrar la información de los servicios de entrenamiento disponibles para mantener organizado el catálogo de servicios del gimnasio. | Should Have | WEB | Propuesto | HU-03 |
| **HU-22** | E05 · Suplementos deportivos y promociones | Registrar suplementos | Como administrador, quiero registrar los suplementos deportivos con sus características, imágenes y precios para disponer de un catálogo digital de productos. | Must Have | WEB | Propuesto | HU-03 |
| **HU-23** | E05 · Suplementos deportivos y promociones | Actualizar catálogo | Como administrador, quiero actualizar la información y disponibilidad de los suplementos para mantener vigente el catálogo ofrecido a los clientes. | Must Have | WEB | Propuesto | HU-22 |
| **HU-24** | E05 · Suplementos deportivos y promociones | Registrar promociones | Como administrador, quiero registrar promociones de suplementos deportivos para comunicar las ofertas disponibles. | Must Have | WEB | Propuesto | HU-22 |
| **HU-25** | E05 · Suplementos deportivos y promociones | Consultar suplementos | Como cliente, quiero consultar información sobre los suplementos disponibles para conocer sus características antes de contactar con el gimnasio. | Must Have | APP | Propuesto | HU-22, HU-28 |
| **HU-26** | E05 · Suplementos deportivos y promociones | Consultar promociones | Como cliente, quiero consultar las promociones vigentes para conocer las ofertas comerciales del gimnasio. | Should Have | APP | Propuesto | HU-24, HU-28 |
| **HU-27** | E05 · Suplementos deportivos y promociones | Consultar preguntas frecuentes | Como cliente, quiero acceder a información y respuestas frecuentes sobre suplementos para resolver consultas generales sin depender siempre de recepción. | Should Have | APP / MICRO | Propuesto | HU-15, HU-22 |
| **HU-28** | E06 · Aplicación móvil del cliente | Inicio de sesión móvil | Como cliente registrado, quiero iniciar sesión desde la aplicación móvil para acceder de manera segura a mi información personal. | Must Have | APP | Propuesto | HU-03, HU-05 |
| **HU-29** | E06 · Aplicación móvil del cliente | Consultar membresía móvil | Como cliente, quiero consultar mi membresía, fecha de vencimiento y condiciones del plan contratado para conocer el estado de mi servicio. | Must Have | APP | Propuesto | HU-09, HU-28 |
| **HU-30** | E06 · Aplicación móvil del cliente | Consultar pagos móvil | Como cliente, quiero consultar el historial de mis pagos registrados para realizar un seguimiento de mis mensualidades. | Must Have | APP | Propuesto | HU-08, HU-28 |
| **HU-31** | E06 · Aplicación móvil del cliente | Visualizar rutinas móvil | Como cliente, quiero visualizar las rutinas que me han sido asignadas para consultar mi planificación de entrenamiento. | Must Have | APP | Pendiente de rol | HU-19, HU-28 |
| **HU-32** | E06 · Aplicación móvil del cliente | Consultar servicios móvil | Como cliente, quiero consultar los servicios ofrecidos por el gimnasio para conocer las alternativas disponibles. | Should Have | APP | Propuesto | HU-21, HU-28 |
| **HU-33** | E06 · Aplicación móvil del cliente | Consultar notificaciones móvil | Como cliente, quiero recibir y consultar notificaciones relacionadas con mis servicios para mantenerme informado sobre avisos importantes. | Must Have | APP / MICRO | Propuesto | HU-13, HU-28 |
| **HU-34** | E06 · Aplicación móvil del cliente | Catálogo y promociones móvil | Como cliente, quiero consultar el catálogo y las promociones de suplementos desde la aplicación móvil para conocer los productos ofrecidos por el gimnasio. | Must Have | APP | Propuesto | HU-22, HU-24, HU-28 |

---

## 3. Refinamiento Detallado: Épicas E01 y E02

### 3.1 Épica E01: Gestión de Usuarios y Clientes

#### HU-03: Inicio de sesión (Prioridad P0)
- **Criterios de Aceptación:**
  - CA-01: Autenticación de personal mediante correo institucional y contraseña robusta (mínimo 8 caracteres, mayúscula, minúscula, número y símbolo).
  - CA-02: Respuesta de error genérica ante fallo de autenticación («Credenciales no válidas») sin revelar si el correo existe.
  - CA-03: Límite de 5 intentos fallidos consecutivos con bloqueo temporal de 15 minutos para mitigar ataques de fuerza bruta.
  - CA-04: Cierre de sesión (`logout`) inmediato invalidando la sesión en el servidor.
  - CA-05: Expiración de sesión por inactividad tras 30 minutos.
  - CA-06: Recuperación de acceso mediante token aleatorio criptográfico de un solo uso con caducidad (15 min) enviado únicamente al canal verificado.
  - CA-07: Prohibición absoluta de utilizar contraseñas en texto claro o claves predecibles basadas en DNI o fecha de nacimiento.
- **Subtareas:** `03-01` Modelo Account/Credentials/Session; `03-02` Filtro de seguridad y endpoints `/api/v1/auth`; `03-03` UI Login administrativo con estados de error y carga; `03-04` Pruebas de integración de autenticación.

#### HU-04: Roles y permisos (Prioridad P0 mínimo / P1 completo)
- **Criterios de Aceptación:**
  - CA-01: Cuatro roles del sistema: `SUPERADMIN`, `ADMIN`, `RECEPCION`, `CLIENTE`.
  - CA-02: `RECEPCION` puede consultar y registrar clientes, registrar pagos presenciales y consultar membresías, pero tiene denegada la creación o edición de planes (HTTP 403).
  - CA-03: `ADMIN` puede gestionar planes, clientes y membresías.
  - CA-04: `SUPERADMIN` es el único facultado para aprobar excepciones críticas (anulación económica, alteración de documento) y gestionar cuentas de personal.
  - CA-05: Semilla de datos segura sin credenciales hardcodeadas en producción; contraseñas con hash BCrypt.
  - CA-06: Registro de eventos de auditoría (`AuditEvent`) con actor, acción, instante UTC, zona horaria y datos minimizados.
- **Subtareas:** `04-01` Middleware y anotaciones `@PreAuthorize`; `04-02` Registro de auditoría; `04-03` Pruebas unitarias de autorización por rol.

#### HU-01: Registrar clientes (Prioridad P0)
- **Criterios de Aceptación:**
  - CA-01: Campos obligatorios para adulto: Nombres, Apellidos, Tipo de Documento (`DNI`, `CARNET_EXTRANJERIA`, `PASAPORTE`), Número de Documento, Fecha de Nacimiento, Celular y Correo.
  - CA-02: Validación de unicidad de documento `(country, type, normalized_number)`; si ya existe, devolver HTTP 409 Conflict conservando los datos en el formulario sin revelar datos personales no autorizados.
  - CA-03: Casillas de consentimiento independientes y desmarcadas por defecto:
    - Casilla 1 (Obligatoria): Aceptación explícita de Términos del Servicio (con enlace a versión).
    - Casilla 2 (Obligatoria): Constancia de lectura del Aviso de Privacidad.
    - Casilla 3 (Opcional): Consentimiento para comunicaciones comerciales por correo.
    - Casilla 4 (Opcional): Consentimiento para comunicaciones comerciales por WhatsApp.
  - CA-04: Almacenamiento versionado de la evidencia de consentimiento (`ConsentRecord`) con timestamp UTC, canal e IP/origen.
  - CA-05: Prohibido dar de alta menores de edad en el sistema productivo hasta la validación de reglas de representación y consentimiento legal en la reunión del lunes. En demo, el formulario bloquea la edad menor de 18 años con advertencia explícita.
- **Subtareas:** `01-01` Entidad Client, Document, ConsentRecord con índice único; `01-02` Endpoint `POST /api/v1/clients`; `01-03` Formulario React con validaciones visuales y casillas; `01-04` Pruebas de rechazo de documento duplicado.

#### HU-02: Consultar y actualizar clientes (Prioridad P0 búsqueda / P1 edición)
- **Criterios de Aceptación:**
  - CA-01: Búsqueda ágil de clientes por nombre, apellido o número de documento con paginación.
  - CA-02: Visualización de ficha con estado de contacto (correo verificado, teléfono verificado).
  - CA-03: Recepción puede actualizar celular y correo; el cambio genera evento de auditoría y deja el nuevo contacto en estado `PENDIENTE_VERIFICACION`.
  - CA-04: Alteración de número de documento o nombre legal bloqueada para recepción (requiere solicitud de excepción a SUPERADMIN).
  - CA-05: Historial económico y de pagos inalterable desde la ficha del cliente.
- **Subtareas:** `02-01` Endpoint `GET /api/v1/clients` con filtros y paginación; `02-02` Tabla y buscador en React; `02-03` Endpoint `PATCH /api/v1/clients/{id}/contacts`.

#### HU-05: Habilitar acceso móvil (Prioridad P1)
- **Criterios de Aceptación:**
  - CA-01: Vinculación 1 a 1 entre la ficha `Client` y la cuenta `Account` del socio.
  - CA-02: Activación mediante enlace de un solo uso con caducidad enviado al correo electrónico verificado.
  - CA-03: El socio autenticado únicamente puede ver sus propios datos personales, membresías e historial.
  - CA-04: La consulta de historial permanece disponible aunque la membresía esté vencida.
- **Subtareas:** `05-01` Flujo de invitación y activación; `05-02` Endpoint de activación; `05-03` Pantalla de estado de cuenta en web.

---

### 3.2 Épica E02: Membresías, Pagos y Renovaciones

#### HU-06: Configurar planes (Prioridad P0 primer corte / P1 edición)
- **Criterios de Aceptación:**
  - CA-01: Solo el rol `ADMIN` o `SUPERADMIN` puede registrar y configurar planes de membresía.
  - CA-02: Datos obligatorios: Nombre comercial, Descripción, Duración (en días o meses), Precio regular en PEN (Soles peruanos), Lista de beneficios/condiciones y Estado (`ACTIVO`, `INACTIVO`).
  - CA-03: Validación estricta en backend: nombre no vacío, precio mayor a 0, duración mayor a 0.
  - CA-04: Consulta de catálogo de planes activos e inactivos mediante `GET /api/v1/plans`.
  - CA-05: Los planes persisten en base de datos PostgreSQL / almacenamiento durable y se recargan intactos tras reiniciar el servidor.
  - CA-06 (P1): Edición de plan e inactivación sin alterar las condiciones históricas de membresías ya suscritas (versión snapshot `PlanVersion`).
- **Subtareas:** `06-01` Entidad Plan y PlanVersion; `06-02` Endpoints `POST /api/v1/plans` y `GET /api/v1/plans`; `06-03` Pantalla y formulario en React; `06-04` Pruebas de persistencia y reinicio.

#### HU-07: Asignar membresías & HU-08: Registrar pagos (Prioridad P1)
- **Criterios de Aceptación:**
  - CA-01: Recepción selecciona un cliente registrado y un plan activo vigente.
  - CA-02: Registro de pago **presencial** obligatorio para formalizar el contrato (Medios permitidos: Efectivo, Yape, Plin, Transferencia bancaria, Tarjeta presencial POS). Prohibido pasarela online.
  - CA-03: **Atomicidad transaccional:** La creación de la membresía y el registro del pago se ejecutan en una única transacción de base de datos (`@Transactional`). Si el pago falla o el contrato no puede persistirse, se hace rollback total.
  - CA-04: **Idempotencia:** La solicitud soporta cabecera `Idempotency-Key` para evitar dobles cobros o membresías duplicadas ante reintentos de red o doble clic en recepción.
  - CA-05: Se almacena un snapshot inmutable de las condiciones y precio del plan al momento de contratar (`MembershipTermsSnapshot`).
  - CA-06: Anulaciones o correcciones generan asientos compensatorios con motivo y autorización; nunca borrado físico (`DELETE`).
- **Subtareas:** `07-01` Entidades Membership, PaymentRecord; `07-02` Servicio transaccional `assignMembershipWithPayment`; `07-03` Pruebas de atomicidad y clave idempotente.

#### HU-09: Controlar vigencias & HU-12: Verificar membresía (Prioridad P1 Condicionada)
- **Criterios de Aceptación:**
  - CA-01: Cálculo determinístico del estado de membresía en backend mediante servicio de dominio con reloj inyectado (`Clock`) configurado en zona `America/Lima`.
  - CA-02: Estados del ciclo de vida: `VIGENTE`, `PROXIMA_A_VENCER` (umbral configurable, propuesta: 5 días antes), `VENCIDA`, `SIN_MEMBRESIA`.
  - CA-03: Visualización clara en recepción con badge semántico (icono + texto).
  - CA-04: La regla exacta de inclusión del último día y tolerancia de fin de mes (28/29/30/31) se mantiene rotulada como **Pendiente de validación** hasta la reunión del lunes. No se codifican tolerancias arbitrarias.

#### HU-10: Renovar membresías & HU-11: Listado de vencimientos (Prioridad P1/P2)
- **Criterios de Aceptación:**
  - CA-01: La renovación anticipada inicia el día posterior al vencimiento de la membresía actual.
  - CA-02: La renovación tardía fuera de tolerancia inicia el día en que se registra el pago presencial.
  - CA-03: Listado de vencimientos con filtro por rango de fechas y estado, paginado para recepción.

---

## 4. Matriz de Decisiones: Aprobado / Propuesto / Pendiente

| ID | Tema | Decisión / Propuesta | Estado | Justificación / Próximo Paso |
|---|---|---|---|---|
| **DEC-01** | Arquitectura de Backend | Spring Boot (Java 21/24) como monolito modular con Clean Architecture en `gymvip-backend`. | **APROBADO** | Respeta Charter y stack instalado. |
| **DEC-02** | Frontend Administrativo | React con TypeScript / JavaScript en `gymvip-frontend`, Vanilla CSS y tokens de diseño. | **APROBADO** | Sin frameworks pesados innecesarios; responsive y accesible. |
| **DEC-03** | Aplicación Móvil | Kotlin Multiplatform en `gymvip-mobile` exclusivo para consulta del socio (Tu Espacio, Membresía, Pagos). | **APROBADO** | Definido en Project Charter; sin pagos online. |
| **DEC-04** | Microservicio Comunicaciones | Servicio único desacoplado en `gymvip-comunicaciones` con stubs de prueba para WhatsApp/Email. | **APROBADO** | No envía mensajes reales ni genera costos sin validación. |
| **DEC-05** | Pasarela de Pago | Exclusión total de pasarelas online y tarjetas almacenadas; solo registro presencial de pagos. | **APROBADO** | Requisito explícito del cliente y Charter. |
| **DEC-06** | Roles del Sistema | `SUPERADMIN`, `ADMIN`, `RECEPCION`, `CLIENTE`. Se suprime rol `ENTRENADOR`. | **PROPUESTO** | Requiere ratificación el lunes por conflicto con HU-18–HU-20. |
| **DEC-07** | Consentimiento Adulto | Casillas separadas y desmarcadas para Términos, Privacidad y Marketing con evidencia versionada. | **APROBADO** | Cumple normativa de protección de datos personales. |
| **DEC-08** | Admisión de Menores | Bloqueo temporal en producción hasta definir representante legal, edad mínima y consentimiento. | **PENDIENTE** | Pregunta obligatoria para reunión del lunes. |
| **DEC-09** | Reglas de Vigencia 28–31 | Cálculo con `LocalDate` y zona Lima; días de fin de mes y tolerancia de acceso. | **PENDIENTE** | Requiere casos de prueba fechados firmados por el cliente. |
| **DEC-10** | Mecanismo de Tolerancia | Si el socio puede ingresar tras vencer membresía y por cuántos días. | **PENDIENTE** | Pregunta para reunión del lunes. |

---

## 5. Definición de Terminado (Definition of Done — DoD)

Una Historia de Usuario o incremento se considera **TERMINADO (DONE)** únicamente cuando cumple en su totalidad los siguientes criterios observables:

1. **Código y Arquitectura:**
   - Implementado en las capas correspondientes (Dominio, Aplicación, Infraestructura, API).
   - Tipado estricto, sin claves secretas ni tokens hardcodeados en código fuente.
   - Contratos API documentados con ejemplos y códigos de error estándar.
2. **Persistencia y Datos:**
   - Esquema de base de datos migrado con restricciones de integridad y unicidad.
   - Los datos persisten y pueden recuperarse íntegramente tras reiniciar el servicio.
   - Ninguna operación de borrado físico en entidades financieras o de auditoría.
3. **Seguridad y Permisos:**
   - Endpoints privados protegidos con autenticación y validación de roles en backend.
   - Contraseñas almacenadas con algoritmo de hash seguro (BCrypt/Argon2).
   - Generación de evento de auditoría en operaciones mutables críticas.
4. **Pruebas y Verificación:**
   - Pruebas unitarias de dominio y servicios pasando al 100%.
   - Pruebas de integración de API (caso feliz y casos de error: 400, 401, 403, 409).
   - Compilación exitosa (`build` sin advertencias críticas ni errores).
5. **Interfaz de Usuario (UI):**
   - Interfaz responsive integrada con la API real (no únicamente mocks visuales aislados).
   - Manejo explícito de los 4 estados: Carga (`Loading`), Vacío (`Empty`), Error (`Error`), Éxito (`Success`).
   - Aplicación estricta de tokens del Design System y soporte de movimiento reducido.
6. **Entrega y Control de Versiones:**
   - Commits locales coherentes y atómicos en Git con mensajes descriptivos.
   - Documentación actualizada reflejando las diferencias y decisiones pendientes.
