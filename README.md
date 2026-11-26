# GYM VIP — Backend API REST (`gymvip-backend`)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java Version](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-green.svg)]()
[![Database](https://img.shields.io/badge/PostgreSQL-17%20%7C%2018-blue.svg)]()
[![Flyway](https://img.shields.io/badge/Flyway-V1--V28-red.svg)]()
[![Branch](https://img.shields.io/badge/branch-ACOSTA--MEJORAS--Y--ACTUALIZACIONES-yellow.svg)]()
[![License](https://img.shields.io/badge/license-Academic%20Proprietary-lightgrey.svg)]()

> **Ubicación en el Workspace:** `gymvip-backend/`  
> **Organización Oficial:** [gymvip-upeu/gymvip-backend](https://github.com/gymvip-upeu/gymvip-backend)  
> **Documentación Centralizada:** `../documentos/`  
> **Fecha de Actualización:** 23 de Noviembre de 2026 | Revisión Canónica v2.5

---

## 1. Nombre del Componente
**GYM VIP Backend REST API Service** (`gymvip-backend`).

---

## 2. Propósito
Constituye el núcleo transaccional, autoritativo y de persistencia del ecosistema **GYM VIP / VIP CENTER FIT**. Es el único componente autorizado para certificar estados de cobertura, liquidar cobros presenciales y digitales, gestionar inventario físico bajo concurrencia pesimista y proveer métricas analíticas agregadas para la toma de decisiones gerenciales y operativas.

---

## 3. Responsabilidades
- **Autenticación y Sesiones:** Emisión, validación y revocación inmediata de JSON Web Tokens (RFC 7519 HS256) con persistencia de sesiones activas y cierre concurrente remoto.
- **Control de Acceso y RBAC:** Evaluación autoritativa de roles (`ADMIN`, `RECEPCION`, `AUDITOR`, `CLIENTE`) y verificación manual o biométrica de ingreso.
- **Gestión de Membresías y Cobros:** Cotización, congelamiento de tarifas históricas (`plan_versions`), emisión de constancias internas (`VIP-...`) y encadenamiento continuo de vigencias sin prórrogas ficticias.
- **Control de Asistencia QR Invertido:** Generación de desafíos QR temporales (TTL 20s) y procesamiento criptográfico de marcaciones de torniquete/mostrador.
- **Tienda de Suplementos y Kardex:** Manejo de carrito persistido por usuario, reserva temporal de stock por 30 minutos, bloqueo `SELECT FOR UPDATE` anti-sobreventa, verificación manual de comprobantes y emisión de tickets de retiro QR (`rtk_...`).
- **Dashboard Analítico:** Agregación de alto rendimiento con `JdbcTemplate` para KPIs de 5 niveles con aislamiento financiero según rol.
- **Integraciones y Eventos:** Despacho hacia microservicio de comunicaciones y orquestación de pagos digitales CulqiLink.

---

## 4. Arquitectura
Implementa una **Arquitectura en Capas (Layered Architecture)** con separación estricta de responsabilidades e inyección de dependencias por constructor:
- **Capa de Controladores (`controller`):** Exposición de contratos REST `/api/v1`, serialización DTO inmutable y control RBAC declarativo.
- **Capa de Negocio y Servicios (`service`):** Orquestación transaccional (`@Transactional`), aplicación de reglas de negocio, cálculo de vigencias en zona horaria `America/Lima` (UTC-5).
- **Capa de Persistencia (`repository`):** Spring Data JPA / Hibernate 6 optimizado y consultas nativas SQL de agregación con `JdbcTemplate`.
- **Capa de Dominio y Entidades (`entity`):** Modelado relacional mapeado a esquemas de PostgreSQL con restricciones de integridad referencial y de dominio (`CHECK`, `UNIQUE`).
- **Capa de Seguridad (`security`):** Cadena de filtros de Spring Security 6 stateless con `JwtAuthenticationFilter`, `DelegatedAuthenticationEntryPoint` y `SecurityConfig`.

---

## 5. Tecnologías y Versiones Reales
| Tecnología | Versión Oficial | Propósito |
|---|---|---|
| **Java Development Kit (JDK)** | OpenJDK 21 LTS | Lenguaje base con virtual threads y sealed classes |
| **Spring Boot Framework** | 3.4.3 | Framework web, inyección, seguridad y transacciones |
| **Spring Security** | 6.4.3 | Seguridad perimetral stateless y RBAC |
| **PostgreSQL Database** | 17.2 / 18.x compatible | Motor relacional transaccional |
| **Flyway Core & PostgreSQL** | 10.20.1+ administrado | Versionado y migración continua de esquema (V1 a V28) |
| **Hibernate ORM / JPA** | 6.6.8.Final | Mapeo objeto-relacional y validaciones JPA |
| **JSON Web Token (jjwt)** | 0.12.6 | Firmado criptográfico HMAC-SHA256 |
| **Apache POI** | 5.2.5 | Procesamiento y validación de hojas Excel (.xlsx) |
| **JUnit 5 / Mockito** | 5.11.4 / 5.14.2 | Pruebas unitarias, mocks y tests de aislamiento |

---

## 6. Estructura de Carpetas
```text
gymvip-backend/
├── mvnw / mvnw.cmd              # Maven Wrapper reproducible
├── pom.xml                      # Dependencias, plugins y versiones administradas
├── CONTRIBUTING.md              # Guía de contribución y regla de commits
├── README.md                    # Esta documentación canónica
├── src/
│   ├── main/
│   │   ├── java/com/gymvip/
│   │   │   ├── config/          # CORS, Jackson, JdbcTemplate, TimeZone America/Lima
│   │   │   ├── controller/      # 30+ Controladores RESTful (/api/v1/...)
│   │   │   ├── dto/             # Request y Response DTOs inmutables
│   │   │   ├── entity/          # Entidades JPA (Client, Membership, Payment, etc.)
│   │   │   ├── exception/       # GlobalExceptionHandler y excepciones de negocio
│   │   │   ├── repository/      # Interfaces Spring Data JPA y consultas custom
│   │   │   ├── security/        # JwtFilter, JwtTokenProvider, SecurityConfig, UserDetails
│   │   │   └── service/         # Interfaces e implementaciones de lógica de negocio
│   │   └── resources/
│   │       ├── application.properties      # Configuración productiva y placeholders
│   │       ├── application-test.properties # Entorno de pruebas automatizadas
│   │       └── db/migration/               # Scripts Flyway inmutables V1__ a V28__
│   └── test/
│       └── java/com/gymvip/     # Suites de pruebas unitarias y de integración
```

---

## 7. Funcionalidades
1. **Gestión de Socios (HU-01, HU-02):** Alta individual, edición concurrente optimista (409), historial de cambios auditados (`client_changes`) y consulta ApiPeruDev.
2. **Importación Masiva (HU-06):** Procesamiento de archivos `.xlsx`, validación fila por fila, exclusión de registros y resolución de discrepancias.
3. **Catálogo Tarifario (HU-07):** Versionado inmutable de planes (`plan_versions`), duraciones mensuales exactas de 30 días y pases diarios.
4. **Membresías y Cobros (HU-08, HU-10):** Afiliación, renovación continua, registro de cobranza en efectivo/tarjeta/digital con idempotencia y constancia.
5. **Control de Asistencia (HU-12, HU-13):** Verificación manual en recepción y validación por QR invertido temporal (TTL 20s).
6. **Tienda y Pedidos (HU-42, HU-50):** Inventario con bloqueo pesimista, reservas de stock por 30m, validación de referencias bancarias y ticket de retiro QR.
7. **Dashboard Analítico (Fase 13):** Agregaciones SQL nativas para 5 niveles con filtro RBAC (métricas financieras ocultas para recepción).
8. **Auditoría y Supresión LPDP:** Derecho al olvido conforme a la Ley N° 29733 de Protección de Datos Personales de Perú.

---

## 8. Usuarios y Roles Correspondientes
| Rol en BD | Descripción y Acceso | Restricciones de Seguridad |
|---|---|---|
| `ADMIN` | Administrador general de sede | Acceso irrestricto, configuración, staff, finanzas y reportes |
| `RECEPCION` | Colaborador operativo de mostrador | Cobros, asistencias, altas, entregas; **sin acceso financiero ni staff** |
| `AUDITOR` | Inspector contable y de calidad | Solo lectura de métricas, cobranzas y libros de auditoría |
| `CLIENTE` | Socio del gimnasio (App móvil) | Perfil propio, membresía, QR de acceso, catálogo y carrito |

---

## 9. Requisitos Previos
- **Java SE Development Kit:** JDK 21 LTS instalado y verificado (`java -version`).
- **PostgreSQL Database:** PostgreSQL 17.x o 18.x en ejecución en puerto 5432.
- **Maven:** Versión 3.9+ o uso directo del wrapper `./mvnw` / `.\mvnw.cmd`.
- **Git:** Cliente Git configurado con credenciales de la organización.

---

## 10. Instalación desde Cero
```powershell
# 1. Clonar el repositorio oficial
git clone https://github.com/gymvip-upeu/gymvip-backend.git
Set-Location gymvip-backend

# 2. Situarse en la rama de trabajo autorizada
git checkout ACOSTA-MEJORAS-Y-ACTUALIZACIONES

# 3. Crear archivo de variables locales (.env) a partir del ejemplo
Copy-Item .env.example .env

# 4. Compilar y descargar dependencias Maven
.\mvnw.cmd clean compile
```

---

## 11. Variables de Entorno
Configurar en `.env` o en las variables del sistema (nunca incluir secretos en Git):
```properties
# Conexión a Base de Datos PostgreSQL
DB_URL=jdbc:postgresql://localhost:5432/gymvip_db
DB_USERNAME=gymvip_user
DB_PASSWORD=placeholder_seguro_db_pass
DB_SCHEMA=public

# Seguridad y Criptografía
JWT_SECRET=placeholder_jwt_secret_minimo_256_bits_hexadecimal_o_base64
JWT_EXPIRATION_HOURS=24

# Puerto de Ejecución Local
SERVER_PORT=8081

# Pasarela de Pagos Culqi (Modo Pruebas / Sandbox)
CULQI_PUBLIC_KEY=pk_test_placeholder
CULQI_PRIVATE_KEY=sk_test_placeholder
CULQI_WEBHOOK_SECRET=wh_test_placeholder

# Microservicio de Comunicaciones
COMMS_SERVICE_URL=http://localhost:8082

# API de Consulta RENIEC / DNI
API_PERU_TOKEN=placeholder_token_apiperu
```

---

## 12. Configuración Local
Asegurar que la base de datos `gymvip_db` exista en PostgreSQL antes del primer arranque:
```sql
CREATE DATABASE gymvip_db WITH ENCODING 'UTF8';
```
Flyway ejecutará automáticamente las migraciones desde `V1__init_schema.sql` hasta `V28__...` durante el inicio del contexto de Spring Boot.

---

## 13. Comandos de Ejecución
```powershell
# Ejecución en modo desarrollo con recarga
.\mvnw.cmd spring-boot:run

# Ejecución especificando perfil de pruebas
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=test
```
El servidor quedará escuchando en `http://localhost:8081`.

---

## 14. Comandos de Compilación
```powershell
# Empaquetado de producción omitiendo pruebas (para despliegue)
.\mvnw.cmd clean package -DskipTests

# El artefacto ejecutable se generará en:
# target/gymvip-backend-0.0.1-SNAPSHOT.jar
```

---

## 15. Pruebas
```powershell
# Ejecutar todas las pruebas unitarias y de dominio (103+ tests)
.\mvnw.cmd test

# Ejecutar una suite específica (ej. Dashboard o Cobros)
.\mvnw.cmd test -Dtest=DashboardUnitTest
.\mvnw.cmd test -Dtest=CoveragePolicyTest,MembershipDatesTest
```

---

## 16. Integraciones
- **Culqi Pagos Online:** Generación de órdenes de pago (`POST /api/v1/payments/culqilink/orders`), verificación y webhook de cargos.
- **gymvip-ms-comunicaciones:** Despacho REST asíncrono de recordatorios WhatsApp y correos transaccionales.
- **Firebase FCM:** Almacenamiento seguro de tokens de dispositivo móvil para notificaciones push de cobro y recordatorio.
- **ApiPeruDev:** Consulta de identidad por DNI peruano con caché transaccional de 1 hora.

---

## 17. Seguridad
- **Políticas Stateless:** Sin almacenamiento de estado de sesión en servidor; cada petición se autentica mediante cabecera `Authorization: Bearer <token>`.
- **RBAC Estricto:** Métodos protegidos con `@PreAuthorize("hasRole('ADMIN')")`. El endpoint de Dashboard filtra los balances financieros (`financialSummary = null`) para el rol `RECEPCION`.
- **Protección de Inyección:** Uso exclusivo de parámetros nombrados en `JdbcTemplate` y consultas tipadas JPA.
- **Protección LPDP:** Enmascaramiento de datos personales en logs y endpoint formal de eliminación de cuenta.

---

## 18. Despliegue
- **Plataforma Productiva de Referencia:** Render Web Service (o Railway / AWS ECS).
- **Comando de Inicio en Contenedor:**
  ```bash
  java -Dserver.port=$PORT -Dspring.profiles.active=prod -jar target/gymvip-backend-0.0.1-SNAPSHOT.jar
  ```
- **Healthcheck:** `GET /actuator/health` o `GET /api/v1/coverage/public/health`.

---

## 19. Solución de Problemas Frecuentes
- **Error `Connection to localhost:5432 refused`:** PostgreSQL no está iniciado o las credenciales en `.env` son incorrectas.
- **Error `FlywayValidateException: Validate failed`:** Se modificó un script SQL ya aplicado en la base de datos. NUNCA editar migraciones previas; crear una nueva versión `V29__...`.
- **Error 403 Forbidden en operaciones administrativas:** El token JWT enviado no tiene el claim de rol `ADMIN` o ha expirado.

---

## 20. Limitaciones Conocidas
- La pasarela automática Culqi opera actualmente en entorno Sandbox con confirmación manual de contingencia en mostrador.
- Las consultas de agregación analítica están optimizadas para la zona `America/Lima` (UTC-5); entornos con desfase de reloj del host requerirán NTP sincronizado.

---

## 21. Enlaces hacia los Otros Repositorios
- **Frontend Web:** [gymvip-frontend](https://github.com/gymvip-upeu/gymvip-frontend)
- **App Móvil:** [gymvip-mobile](https://github.com/gymvip-upeu/gymvip-mobile)
- **Comunicaciones:** [gymvip-ms-comunicaciones](https://github.com/gymvip-upeu/gymvip-ms-comunicaciones)
- **Portal Público:** [gymvip-Landing-Page](https://github.com/gymvip-upeu/gymvip-Landing-Page)

---

## 22. Enlaces hacia Diagramas
- **C4 L1, L2, L3:** `../documentos/diagramas/01-C4/` y `../documentos/c4.uml`
- **Modelo Relacional ERD:** `../documentos/diagramas/02-Base-de-Datos/`
- **Flujos Funcionales:** `../documentos/diagramas/09-Flujos-Funcionales/`
- **Seguridad y RBAC:** `../documentos/diagramas/12-Seguridad-Usuarios-Roles/`

---

## 23. Enlaces hacia ESTADO_PROYECTO.md
- Documento Central Canónico: [`../documentos/ESTADO_PROYECTO.md`](../documentos/ESTADO_PROYECTO.md)
- Registro de Tareas Maestras: [`../documentos/seguimiento/PLAN_MAESTRO_GYMVIP.md`](../documentos/seguimiento/PLAN_MAESTRO_GYMVIP.md)

---

## 24. Fecha y Revisión de Referencia
- **Fecha:** 23 de Noviembre de 2026.
- **Revisión de Ingeniería:** Consolidación Maestra Sprint 1 & 2 / Release Candidate v2.5.
- **Autoría:** Equipo de Ingeniería GYM VIP / VIP CENTER FIT.


---

## 25. Actualización Enterprise & RBAC (25/11/2026 — Rama `develop`)
- **Jerarquía y RBAC Granular:** 21 roles (`Role.java`) y 42 permisos (`PermissionCatalog.java`) inyectados en JWT/Filter (`TokenAuthenticationFilter.java`), más endpoints `/api/v1/rbac/**`.
- **Seguridad Privilegiada & MFA:** Guardias anti-autoinactivación, anti-autodegradación y anti-escalada en `AccountServiceImpl.java`; TOTP RFC 6238 + recovery codes SHA-256 en `AccountSecurityController.java`; rate limiting por usuario/rol de 5 minutos (`LoginRateLimitPolicy.java`).
- **Esquema Aditivo V31:** `V31__enterprise_rbac_hierarchy_and_training.sql` (`organizations`, `branches`, `account_branch_assignments`, `user_preferences`, `exercises`, `workout_routines*`, `workout_sessions`, `gym_classes`, `class_reservations`).
- **APIs Enterprise & Training:** `/api/v1/preferences`, `/api/v1/enterprise/gyms|branches`, `/api/v1/training/**` y `/api/v1/accounts/audit`.
- **Verificación:** `202/202` pruebas unitarias Maven aprobadas (`EnterpriseRbacAndProtectionTest` incluido). Documentación completa en `../documentos/INFORME_EVOLUCION_ENTERPRISE_MOBILE.md`.
