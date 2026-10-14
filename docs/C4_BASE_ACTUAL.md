# Modelo de Arquitectura C4 — Base Funcional GYM VIP
**Responsable:** André Acosta (Sprint 1)  
**Alcance Técnico:** HU-01 (Registro de Clientes), HU-04 (Personal y Roles), HU-06 (Catálogo de Planes)  
**Fecha:** Octubre 2026  

---

## Nivel 1: Diagrama de Contexto del Sistema (C1)

Muestra los usuarios que interactúan con el sistema GYM VIP y sus fronteras operativas en la Base Sprint 1.

```mermaid
flowchart TD
    subgraph Actores["Actores del Gimnasio"]
        Admin["👤 Administrador (ADMIN)<br/>Gestiona planes, personal, roles y socios"]
        Recepcion["👤 Recepcionista (RECEPCION)<br/>Registra socios y consulta catálogo de planes"]
    end

    subgraph SistemaGYMVIP["Sistema GYM VIP (Base Acosta)"]
        Plataforma["🏢 Plataforma GYM VIP<br/>[React SPA + Spring Boot API + PostgreSQL]<br/>Permite el alta de socios, control RBAC y tarifario"]
    end

    subgraph Futuro["Sistemas Futuros / Otros Repositorios (Fuera de Alcance)"]
        Mobile["📱 App Móvil Socios<br/>(gymvip-mobile - Excluido de esta base)"]
        Comms["✉️ Microservicio de Comunicaciones<br/>(gymvip-comunicaciones - Excluido de esta base)"]
    end

    Admin -->|"Autenticación, personal, roles, planes y socios [HTTPS/JSON]"| Plataforma
    Recepcion -->|"Autenticación, consulta de planes y registro de socios [HTTPS/JSON]"| Plataforma
    Plataforma -.->|"Reserva de contratos para integración futura"| Futuro

    classDef actor fill:#1D2330,stroke:#D4AF37,stroke-width:2px,color:#F5F5F4;
    classDef system fill:#151922,stroke:#D4AF37,stroke-width:3px,color:#D4AF37;
    classDef external fill:#11141B,stroke:#303746,stroke-width:1px,stroke-dasharray: 5 5,color:#7E8B9B;

    class Admin,Recepcion actor;
    class Plataforma system;
    class Mobile,Comms external;
```

---

## Nivel 2: Diagrama de Contenedores (C2)

Describe la distribución de software ejecutable y sus protocolos de comunicación.

```mermaid
flowchart TD
    subgraph Cliente["Navegador Web / Dispositivo del Personal"]
        SPA["💻 Aplicación Web SPA (Frontend)<br/>[React 18 + Vite + Vanilla CSS Luxury Dark/Gold]<br/>Proporciona interfaz gráfica responsiva, validaciones y shell con sidebar plegable"]
    end

    subgraph Servidor["Backend / Servidor de Aplicaciones (Render / Local)"]
        API["⚙️ API RESTful (Backend Monolito Modular)<br/>[Spring Boot 3.4.3 + Java 21 + Spring Security]<br/>Aplica Clean Architecture, reglas de negocio puras, RBAC y auditoría"]
    end

    subgraph Persistencia["Base de Datos Relacional"]
        DB[("🗄️ Base de Datos Relacional<br/>[PostgreSQL 16+ / Render / Flyway Migrations]<br/>Almacena cuentas, sesiones, documentos, socios, planes y versiones")]
    end

    SPA -->|"HTTPS / REST JSON<br/>(Lee VITE_API_BASE_URL, Bearer Token)"| API
    API -->|"JDBC over TLS (HikariCP)<br/>(Lee DB_JDBC_URL, DB_USERNAME, DB_PASSWORD)"| DB

    classDef container fill:#151922,stroke:#D4AF37,stroke-width:2px,color:#F5F5F4;
    classDef db fill:#11141B,stroke:#D4AF37,stroke-width:2px,color:#D4AF37;

    class SPA,API container;
    class DB db;
```

---

## Nivel 3: Diagrama de Componentes de Backend (C3 - Clean Architecture)

Ilustra la separación estricta entre capas y contextos del backend dentro del paquete raíz `com.gymvip`.

```mermaid
flowchart TB
    subgraph CapaAPI["Capa API / Web (Entrada HTTP & DTOs)"]
        AuthController["AuthController<br/>/api/v1/auth"]
        AccountController["AccountController<br/>/api/v1/accounts"]
        ClientController["ClientController<br/>/api/v1/clients"]
        PlanController["PlanController<br/>/api/v1/plans"]
        TokenAuthFilter["TokenAuthenticationFilter<br/>(Valida Bearer Token)"]
        GlobalExceptionAdvice["GlobalExceptionHandler<br/>(400, 401, 403, 404, 409)"]
    end

    subgraph CapaApplication["Capa Application (Casos de Uso & Puertos)"]
        subgraph InboundPorts["Puertos de Entrada (Interfaces)"]
            AuthUC["AuthUseCase"]
            StaffUC["StaffAccountUseCase"]
            ClientRegUC["RegisterClientUseCase"]
            ClientSearchUC["SearchClientsUseCase"]
            PlanUC["ManagePlanUseCase"]
            AuditUC["AuditUseCase"]
        end

        subgraph UseCaseServices["Implementación de Casos de Uso (Servicios de Aplicación)"]
            AuthService["AuthService"]
            StaffService["StaffAccountService"]
            ClientRegService["RegisterClientService"]
            ClientSearchService["SearchClientsService"]
            PlanService["ManagePlanService"]
            AuditService["AuditService"]
        end
    end

    subgraph CapaDomain["Capa Domain (Modelos Puros & Reglas Invariantes)"]
        AccountModel["Account / Session"]
        ClientModel["Client / Document / MemberCodeGenerator"]
        PlanModel["Plan / PlanVersion (Snapshot Inmutable)"]
        AuditModel["AuditEvent"]
        DomainExceptions["Domain Exceptions<br/>(ClientValidation, DuplicatePlan, LastAdminProtection, etc.)"]
    end

    subgraph CapaInfrastructure["Capa Infrastructure (Persistencia, Seguridad, Adaptadores)"]
        subgraph OutboundPorts["Puertos de Salida (Interfaces)"]
            AccountPort["AccountPersistencePort / SessionPersistencePort"]
            ClientPort["ClientPersistencePort"]
            PlanPort["PlanPersistencePort"]
            AuditPort["AuditPersistencePort"]
        end

        subgraph Adapters["Adaptadores de Persistencia & Repositorios Spring Data"]
            AccountAdapter["AccountPersistenceAdapter<br/>SpringDataAccountRepository"]
            ClientAdapter["ClientPersistenceAdapter<br/>SpringDataClientRepository<br/>SpringDataDocumentRepository"]
            PlanAdapter["PlanPersistenceAdapter<br/>SpringDataPlanRepository<br/>SpringDataPlanVersionRepository"]
            AuditAdapter["AuditPersistenceAdapter<br/>SpringDataAuditEventRepository"]
        end
    end

    %% Relaciones Capa API -> Application
    AuthController --> AuthUC
    AccountController --> StaffUC
    ClientController --> ClientRegUC
    ClientController --> ClientSearchUC
    PlanController --> PlanUC
    TokenAuthFilter --> AuthUC

    %% Relaciones Inbound Ports -> Services
    AuthUC <|.. AuthService
    StaffUC <|.. StaffService
    ClientRegUC <|.. ClientRegService
    ClientSearchUC <|.. ClientSearchService
    PlanUC <|.. PlanService
    AuditUC <|.. AuditService

    %% Relaciones Application -> Domain
    AuthService --> AccountModel
    StaffService --> AccountModel
    ClientRegService --> ClientModel
    ClientSearchService --> ClientModel
    PlanService --> PlanModel
    AuditService --> AuditModel
    UseCaseServices --> DomainExceptions

    %% Relaciones Application -> Outbound Ports
    AuthService --> AccountPort
    StaffService --> AccountPort
    ClientRegService --> ClientPort
    ClientSearchService --> ClientPort
    PlanService --> PlanPort
    AuditService --> AuditPort
    UseCaseServices --> AuditUC

    %% Relaciones Outbound Ports -> Infrastructure Adapters
    AccountPort <|.. AccountAdapter
    ClientPort <|.. ClientAdapter
    PlanPort <|.. PlanAdapter
    AuditPort <|.. AuditAdapter

    classDef apiLayer fill:#1D2330,stroke:#60A5FA,stroke-width:1px,color:#F5F5F4;
    classDef appLayer fill:#151922,stroke:#34D399,stroke-width:1px,color:#F5F5F4;
    classDef domainLayer fill:#11141B,stroke:#D4AF37,stroke-width:2px,color:#D4AF37;
    classDef infraLayer fill:#151922,stroke:#F59E0B,stroke-width:1px,color:#F5F5F4;

    class AuthController,AccountController,ClientController,PlanController,TokenAuthFilter,GlobalExceptionAdvice apiLayer;
    class AuthUC,StaffUC,ClientRegUC,ClientSearchUC,PlanUC,AuditUC,AuthService,StaffService,ClientRegService,ClientSearchService,PlanService,AuditService appLayer;
    class AccountModel,ClientModel,PlanModel,AuditModel,DomainExceptions domainLayer;
    class AccountPort,ClientPort,PlanPort,AuditPort,AccountAdapter,ClientAdapter,PlanAdapter,AuditAdapter infraLayer;
```
