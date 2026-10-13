# Registro de Decisiones de Arquitectura y Producto (ADR / PDR) — GYM VIP

**Fecha:** 11 de octubre de 2026  
**Proyecto:** GYM VIP (VIP CENTER FIT S.A.C.)  
**Estados de decisión:** `APROBADO`, `PROPUESTO`, `PENDIENTE`

---

## 1. Decisiones Aprobadas (Base técnica y contractual firme)

### DEC-01: Stack de Backend

- **Estado:** `APROBADO`
- **Contexto:** Se requiere un backend robusto, escalable y mantenible para gestionar clientes, membresías, pagos presenciales y seguridad.
- **Decisión:** Monolito modular en Spring Boot (Java 21/24 LTS) organizado en paquetes por contexto delimitado (`identity`, `clients`, `plans`, `memberships`, `payments`, `audit`, `shared`) aplicando principios de Clean Architecture.
- **Consecuencias:** Mantiene la inversión tecnológica del equipo, asegura soporte LTS y compatibilidad con JPA / PostgreSQL.

### DEC-02: Base de Datos y Persistencia

- **Estado:** `APROBADO`
- **Contexto:** La aplicación requiere persistencia relacional con garantías ACID para membresías y pagos.
- **Decisión:** PostgreSQL como motor de base de datos en entornos de producción / Docker, con compatibilidad transparente para base de datos persistente basada en archivo en perfiles de desarrollo local (`dev/local`) sin dependencias externas obligatorias para demos inmediatas.
- **Consecuencias:** Integridad referencial fuerte, soporte de transacciones `@Transactional`, unicidad de documentos mediante índices compuestos y aislamiento de concurrencia.

### DEC-03: Frontend Administrativo

- **Estado:** `APROBADO`
- **Contexto:** Recepción y administración operan desde estaciones de trabajo de escritorio y tabletas.
- **Decisión:** React SPA con Vite, Vanilla CSS modular y tokens formales de diseño extraídos de `DESIGN_SYSTEM.md`. Sin dependencias pesadas innecesarias.
- **Consecuencias:** Carga rápida, navegación fluida, estricto control de accesibilidad y estados de interfaz consistentes.

### DEC-04: Exclusión de Pasarela de Pagos Online

- **Estado:** `APROBADO`
- **Contexto:** Se evaluó si la aplicación debía cobrar membresías por internet.
- **Decisión:** Exclusión explícita de pasarelas de pago y almacenamiento de tarjetas bancarias. Los cobros son exclusivamente presenciales (efectivo, transferencias, billeteras digitales Yape/Plin, POS físico).
- **Consecuencias:** Reduce drásticamente la superficie de ataque, elimina cumplimiento PCI-DSS complejo y se alinea con la operativa real del gimnasio.

### DEC-05: Consentimiento Informado Adulto

- **Estado:** `APROBADO`
- **Contexto:** Normativa de Protección de Datos Personales (Ley N° 29733 en Perú).
- **Decisión:** Casillas separadas y desmarcadas por defecto para:
  1. Términos del servicio (requerida).
  2. Aviso de privacidad (requerida).
  3. Promociones por correo (opcional).
  4. Promociones por WhatsApp (opcional).
     Se registra evidencia versionada en `ConsentRecord` con instante UTC, IP y canal.
- **Consecuencias:** Pleno cumplimiento legal y trazabilidad de consentimientos y revocaciones.

---

## 2. Decisiones Propuestas (En revisión del equipo técnico)

### DEC-06: Matriz de Roles sin Rol «Entrenador»

- **Estado:** `PROPUESTO`
- **Contexto:** El Project Charter histórico mencionaba entrenadores y rutinas (HU-18 a HU-20). Sin embargo, el gimnasio actualmente no cuenta con entrenadores de planta asignados al sistema y la nueva propuesta de seguridad simplifica la gestión a `SUPERADMIN`, `ADMIN`, `RECEPCION` y `CLIENTE`.
- **Propuesta:** Implementar exclusivamente los 4 roles mencionados. Las HU de rutinas quedan en estado de espera hasta confirmación del patrocinador.
- **Riesgo:** Conflicto con HU-18–HU-20 del backlog original. Se someterá a votación en la reunión del lunes.

### DEC-07: Unicidad y Tratamiento de Identidad

- **Estado:** `PROPUESTO`
- **Contexto:** ¿Qué campos definen unívocamente al socio?
- **Propuesta:** Clave única compuesta en base de datos: `(country_code, document_type, normalized_document_number)`. Ni el correo electrónico ni el número celular son claves únicas de la persona física (pueden compartirse en familias).
- **Consecuencias:** Permite que padres e hijos o parejas compartan contacto de referencia sin bloquear el alta.

---

## 3. Decisiones Pendientes (Bloqueadas para validación del lunes 13/10/2026)

### DEC-08: Política de Admisión y Consentimiento de Menores de Edad

- **Estado:** `PENDIENTE`
- **Pregunta para el cliente:** ¿VIP CENTER FIT acepta socios menores de 18 años? ¿A partir de qué edad mínima? ¿Qué documento se exige a cada uno y quién suscribe el consentimiento?
- **Medida provisional:** Alta de menores bloqueada en el sistema productivo; prototipos rotulados con «Flujo por validar».

### DEC-09: Reglas de Vigencia Calendaria (Días 28, 29, 30 y 31)

- **Estado:** `PENDIENTE`
- **Pregunta para el cliente:** Si un cliente contrata un plan mensual el 31 de enero, ¿su vigencia vence el 28 de febrero o el 2 de marzo? ¿El último día de vigencia es inclusivo hasta las 23:59:59 o hasta el cierre del local?
- **Medida provisional:** Se utiliza `LocalDate.plusMonths(1)` como hipótesis de diseño en pruebas preliminares, sujeta a ratificación de tabla de ejemplos fechada.

### DEC-10: Política de Tolerancia y Gracia para Ingreso

- **Estado:** `PENDIENTE`
- **Pregunta para el cliente:** ¿Existe un período de gracia de 24–48 horas para ingresar al gimnasio tras el vencimiento de una membresía? Si el socio renueva durante ese período de gracia, ¿el nuevo periodo se calcula desde la fecha de vencimiento original o desde la fecha del pago?
- **Medida provisional:** El sistema reporta `VENCIDA` inmediatamente al finalizar la vigencia; la tolerancia se prototipa como variante condicional sin codificar reglas no pactadas.

psarela de pagos esta por ver,
no hay devoluciones y proponme un aforma para que se e pued ainformar o enseñarle que no hay devoluciones
cancelar una membresia
con q medio seria para inciar sesion y queda tos son los necesario para el usuario para que confirme el usairo fianl, pero para eso quiero que sea alguna foram mas facil y funcional y seguro ya que lo del whatsapp aun se implementaria con el microservicio de comunicacione y lo del correo tmb
no hya problema con mensore creeria q no los registran
, tmb toma en cuenta q hay gente q va a diario y solo paaga un dia y solo tiene acceso por ese dia, y habrai un apartado de reportes donde se vea el consolidadod de todo y permita ver segun plan y los q pagan diario
sistema eliminar o desafiliar la membresia y automaticamente quitar ese monto y recargar el monto total del sitema y filtros
no hay tolerancia y al vencer pasaria a inactivo cuando vence, y si paga se le volveria a editar y añadir un plan y si es antes de q venza al ver el recepcionista q se realizo el pago con un bton de renovar haria que se alargue su membresia 30 dias mas
de renovacion q le habilite o marque un mes mas de membrecia,
no va seguimineto de rutina y entenadores, 30 dias la membresia. roles
admin recepcion, y el usuario q seria para el movil,
tener en cuenta que para los colores de todos los diseños seran tonos oscuros y dorados para que se vea premium y vip como dice su nombre
