-- ==============================================================================
-- GYM VIP 2.0 — MIGRACIÓN FLYWAY V33 (ESTRICTAMENTE ADITIVA SOBRE V32)
-- Plataforma SaaS B2B2C Multiempresa, Licenciamiento, Comisiones Calculadas,
-- Afiliación de Gimnasios, Perfil Comercial, Promociones Grupales, Reseñas
-- Verificadas, Perfil Físico Voluntario, Planificador Semanal, Versiones APK
-- y Consentimientos Legales LPDP (Ley 29733 + D.S. 016-2024-JUS).
-- ==============================================================================

-- 1. Catálogo de Planes SaaS B2B (GYM VIP Plataforma -> Empresas/Gimnasios)
CREATE TABLE IF NOT EXISTS saas_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    monthly_fee_pen NUMERIC(12,2) NOT NULL CHECK (monthly_fee_pen >= 0),
    annual_fee_pen NUMERIC(12,2) NOT NULL CHECK (annual_fee_pen >= 0),
    max_branches INT NOT NULL DEFAULT 1 CHECK (max_branches > 0),
    max_users INT NOT NULL DEFAULT 15 CHECK (max_users > 0),
    transaction_commission_pct NUMERIC(6,4) NOT NULL DEFAULT 0.0300,
    included_modules_json TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. Licencias por Empresa / Gimnasio (Ciclo de vida completo + límites)
CREATE TABLE IF NOT EXISTS saas_licenses (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    saas_plan_id UUID NOT NULL REFERENCES saas_plans(id),
    status VARCHAR(30) NOT NULL DEFAULT 'TRIAL', -- TRIAL, ACTIVA, POR_VENCER, VENCIDA, SUSPENDIDA, CANCELADA, DEMO
    started_at DATE NOT NULL DEFAULT CURRENT_DATE,
    expires_at DATE NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    max_branches INT NOT NULL DEFAULT 1 CHECK (max_branches > 0),
    max_users INT NOT NULL DEFAULT 15 CHECK (max_users > 0),
    enabled_modules_json TEXT NOT NULL DEFAULT '[]',
    allow_data_export BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(600),
    updated_by VARCHAR(150),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_license_org UNIQUE (organization_id)
);

-- 3. Reglas y Ledger de Comisiones Calculadas (Sin split fake de Culqi)
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE, -- NULL = regla global por defecto
    saas_plan_id UUID REFERENCES saas_plans(id),
    operation_type VARCHAR(40) NOT NULL, -- MEMBERSHIP, SUPPLEMENT, GROUP_ENROLLMENT, DIGITAL_PASS
    commission_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE', -- PERCENTAGE, FIXED, HYBRID
    percentage_rate NUMERIC(6,4) NOT NULL DEFAULT 0.0300 CHECK (percentage_rate >= 0),
    fixed_fee_pen NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (fixed_fee_pen >= 0),
    gateway_passthrough_pct NUMERIC(6,4) NOT NULL DEFAULT 0.0344 CHECK (gateway_passthrough_pct >= 0),
    applies_only_settled BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(150),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS commission_ledger (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    operation_type VARCHAR(40) NOT NULL, -- MEMBERSHIP, SUPPLEMENT, GROUP_ENROLLMENT
    source_operation_id UUID NOT NULL,
    source_reference VARCHAR(120),
    payment_status VARCHAR(30) NOT NULL, -- Solo PAID / SETTLED se liquidan
    gross_amount_pen NUMERIC(12,2) NOT NULL CHECK (gross_amount_pen >= 0),
    gateway_fee_pen NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (gateway_fee_pen >= 0),
    platform_commission_pen NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (platform_commission_pen >= 0),
    gym_net_amount_pen NUMERIC(12,2) NOT NULL DEFAULT 0,
    applied_rule_id UUID REFERENCES commission_rules(id),
    ledger_status VARCHAR(30) NOT NULL DEFAULT 'CALCULADA', -- CALCULADA, FACTURADA, COBRADA, ANULADA, REEMBOLSADA
    settled_period VARCHAR(20) NOT NULL,
    notes VARCHAR(500),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_commission_source UNIQUE (operation_type, source_operation_id)
);

-- 4. Solicitudes de Afiliación B2B (Landing Page -> Consola SUPER_ADMIN -> Activación)
CREATE TABLE IF NOT EXISTS organization_requests (
    id UUID PRIMARY KEY,
    tracking_code VARCHAR(32) NOT NULL UNIQUE,
    business_name VARCHAR(180) NOT NULL,
    commercial_name VARCHAR(180),
    ruc VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    address VARCHAR(250),
    contact_name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(160) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    branches_count INT NOT NULL DEFAULT 1 CHECK (branches_count > 0),
    estimated_members INT NOT NULL DEFAULT 100 CHECK (estimated_members > 0),
    requested_plan_code VARCHAR(40) NOT NULL DEFAULT 'PRO',
    message VARCHAR(800),
    status VARCHAR(30) NOT NULL DEFAULT 'RECIBIDA', -- RECIBIDA, EN_REVISION, OBSERVADA, APROBADA, INVITACION_ENVIADA, ACTIVADA, RECHAZADA, ARCHIVADA
    review_notes VARCHAR(600),
    reviewed_by VARCHAR(150),
    approved_organization_id UUID REFERENCES organizations(id),
    activation_token VARCHAR(120),
    legal_accepted BOOLEAN NOT NULL DEFAULT TRUE,
    legal_version VARCHAR(30) NOT NULL DEFAULT 'v2.0-2026',
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 5. Perfil Comercial Público por Gimnasio (Vitrina App / Web B2C)
CREATE TABLE IF NOT EXISTS organization_commercial_profiles (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    slug VARCHAR(100) UNIQUE,
    headline VARCHAR(180),
    tagline VARCHAR(300),
    about_text VARCHAR(1500),
    amenities_json TEXT NOT NULL DEFAULT '[]',
    schedule_json TEXT NOT NULL DEFAULT '{}',
    gallery_urls_json TEXT NOT NULL DEFAULT '[]',
    reference_plans_json TEXT NOT NULL DEFAULT '[]',
    contact_whatsapp VARCHAR(40),
    instagram_handle VARCHAR(80),
    website_url VARCHAR(200),
    publish_status VARCHAR(30) NOT NULL DEFAULT 'BORRADOR', -- BORRADOR, PUBLICADO, OCULTO
    published_at_utc TIMESTAMP WITHOUT TIME ZONE,
    updated_by VARCHAR(150),
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. Promociones Grupales e Inscripciones Colectivas (Sin simulación de cobro automático)
CREATE TABLE IF NOT EXISTS group_promotions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    plan_id UUID REFERENCES plans(id),
    min_participants INT NOT NULL DEFAULT 2 CHECK (min_participants >= 2),
    max_participants INT NOT NULL DEFAULT 6 CHECK (max_participants >= 2),
    discount_percent NUMERIC(5,2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 60),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVA', -- ACTIVA, PAUSADA, EXPIRADA, ANULADA
    terms VARCHAR(600),
    created_by VARCHAR(150),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS group_enrollments (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES group_promotions(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    invitation_code VARCHAR(32) NOT NULL UNIQUE,
    leader_account_id UUID NOT NULL REFERENCES accounts(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ABIERTA', -- ABIERTA, COMPLETA, ACTIVADA, EXPIRADA, CANCELADA
    base_price_per_person_pen NUMERIC(12,2) NOT NULL CHECK (base_price_per_person_pen >= 0),
    discounted_price_per_person_pen NUMERIC(12,2) NOT NULL CHECK (discounted_price_per_person_pen >= 0),
    expires_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS group_enrollment_members (
    id UUID PRIMARY KEY,
    group_enrollment_id UUID NOT NULL REFERENCES group_enrollments(id) ON DELETE CASCADE,
    account_id UUID REFERENCES accounts(id),
    invited_email VARCHAR(160) NOT NULL,
    member_role VARCHAR(20) NOT NULL DEFAULT 'INVITADO', -- LIDER, INVITADO
    status VARCHAR(30) NOT NULL DEFAULT 'INVITADO', -- INVITADO, ACEPTADO, RECHAZADO, CANCELADO
    accepted_consent BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at_utc TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_group_enrollment_email UNIQUE (group_enrollment_id, invited_email)
);

-- 7. Reseñas Verificadas de Gimnasios (Solo elegibles con vínculo verificable)
CREATE TABLE IF NOT EXISTS gym_reviews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id),
    author_account_id UUID NOT NULL REFERENCES accounts(id),
    rating_overall INT NOT NULL CHECK (rating_overall BETWEEN 1 AND 5),
    rating_cleanliness INT NOT NULL CHECK (rating_cleanliness BETWEEN 1 AND 5),
    rating_equipment INT NOT NULL CHECK (rating_equipment BETWEEN 1 AND 5),
    rating_attention INT NOT NULL CHECK (rating_attention BETWEEN 1 AND 5),
    rating_value INT NOT NULL CHECK (rating_value BETWEEN 1 AND 5),
    comment VARCHAR(1000) NOT NULL,
    verified_eligibility_source VARCHAR(40) NOT NULL, -- ACTIVE_MEMBERSHIP, PAST_MEMBERSHIP, VERIFIED_ATTENDANCE
    status VARCHAR(30) NOT NULL DEFAULT 'PUBLICADA', -- PUBLICADA, REPORTADA, OCULTA_MODERACION
    gym_reply VARCHAR(800),
    replied_by VARCHAR(150),
    replied_at_utc TIMESTAMP WITHOUT TIME ZONE,
    moderation_reason VARCHAR(400),
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_gym_review_org_author UNIQUE (organization_id, author_account_id)
);

-- 8. Versionado Legal LPDP (Ley 29733 + D.S. 016-2024-JUS) y Consentimientos
CREATE TABLE IF NOT EXISTS legal_document_versions (
    id UUID PRIMARY KEY,
    document_code VARCHAR(40) NOT NULL, -- PRIVACY_POLICY, TERMS_OF_SERVICE, LEGAL_NOTICE
    version VARCHAR(30) NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content_markdown TEXT NOT NULL,
    effective_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_legal_doc_ver UNIQUE (document_code, version)
);

CREATE TABLE IF NOT EXISTS user_legal_acceptances (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
    subject_email VARCHAR(160) NOT NULL,
    document_code VARCHAR(40) NOT NULL,
    document_version VARCHAR(30) NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT TRUE,
    purpose VARCHAR(60) NOT NULL DEFAULT 'PLATFORM_ACCESS',
    ip_address VARCHAR(80),
    user_agent VARCHAR(250),
    accepted_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 9. Preferencias Granulares de Notificaciones (Sin casillas premarcadas en comerciales)
CREATE TABLE IF NOT EXISTS notification_preferences (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    channel_push_operational BOOLEAN NOT NULL DEFAULT TRUE,
    channel_push_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    channel_push_promotions BOOLEAN NOT NULL DEFAULT FALSE,
    channel_email_operational BOOLEAN NOT NULL DEFAULT TRUE,
    channel_email_promotions BOOLEAN NOT NULL DEFAULT FALSE,
    channel_whatsapp_operational BOOLEAN NOT NULL DEFAULT TRUE,
    channel_whatsapp_promotions BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 10. Perfil Físico Voluntario (IMC informativo OMS, NO diagnóstico clínico) y Planificador Semanal
CREATE TABLE IF NOT EXISTS member_physical_profiles (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    height_cm NUMERIC(5,2),
    weight_kg NUMERIC(6,2),
    primary_goal VARCHAR(60),
    experience_level VARCHAR(40),
    weekly_availability_days INT CHECK (weekly_availability_days IS NULL OR weekly_availability_days BETWEEN 1 AND 7),
    injuries_or_notes VARCHAR(600),
    bmi_value NUMERIC(5,2),
    bmi_classification VARCHAR(40),
    consent_saved BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS weekly_training_plans (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    goal VARCHAR(80) NOT NULL,
    level VARCHAR(40) NOT NULL DEFAULT 'PRINCIPIANTE',
    week_start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    days_json TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 11. Manifiesto de Versiones APK y Control de Actualización Segura
CREATE TABLE IF NOT EXISTS app_releases (
    id UUID PRIMARY KEY,
    platform VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    version_code INT NOT NULL,
    version_name VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    download_url VARCHAR(500) NOT NULL,
    sha256_checksum VARCHAR(128) NOT NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    release_notes VARCHAR(800) NOT NULL,
    min_supported_version_code INT NOT NULL DEFAULT 1,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    published_at_utc TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_app_release UNIQUE (platform, version_code)
);

-- Índices para consultas frecuentes y reporting multitenant
CREATE INDEX IF NOT EXISTS idx_commission_ledger_org_period ON commission_ledger(organization_id, settled_period);
CREATE INDEX IF NOT EXISTS idx_org_requests_status ON organization_requests(status, created_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_group_promos_org_status ON group_promotions(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_gym_reviews_org_status ON gym_reviews(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_weekly_plans_account ON weekly_training_plans(account_id, is_active);

-- Semillas iniciales B2B2C coherentes (ON CONFLICT DO NOTHING)
INSERT INTO saas_plans (id, code, name, description, monthly_fee_pen, annual_fee_pen, max_branches, max_users, transaction_commission_pct, included_modules_json, is_active)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'STARTER', 'Starter Sede Única',
     'Ideal para gimnasios independientes de una sola sede con control de socios, membresías, asistencia QR y portal web.',
     149.00, 1490.00, 1, 8, 0.0350,
     '["MEMBERS","MEMBERSHIPS","ATTENDANCE","PLANS","APP_QR","REPORTS_BASIC"]', TRUE),
    ('a1000000-0000-4000-8000-000000000002', 'PRO', 'Pro Multisede',
     'Para cadenas en crecimiento: hasta 5 sedes, tienda de suplementos, promociones grupales, WhatsApp/Email y perfil comercial público.',
     349.00, 3490.00, 5, 35, 0.0300,
     '["MEMBERS","MEMBERSHIPS","ATTENDANCE","PLANS","SUPPLEMENTS","GROUP_PROMOS","COMMS","APP_QR","TRAINING","REVIEWS"]', TRUE),
    ('a1000000-0000-4000-8000-000000000003', 'ENTERPRISE', 'Enterprise Corporativo',
     'Redes multisede sin fricción: sedes ampliadas, RBAC completo, auditoría externa, MFA obligatorio, KPIs ejecutivos y soporte prioritario.',
     749.00, 7490.00, 25, 150, 0.0250,
     '["ALL_MODULES","MULTI_BRANCH","RBAC","AUDIT","MFA","API_WEBHOOKS","WHITELABEL_VITRINE"]', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO saas_licenses (id, organization_id, saas_plan_id, status, started_at, expires_at, auto_renew, max_branches, max_users, enabled_modules_json, allow_data_export, notes, updated_by)
SELECT
    'b1000000-0000-4000-8000-000000000001',
    o.id,
    'a1000000-0000-4000-8000-000000000003',
    'ACTIVA',
    CURRENT_DATE,
    (CURRENT_DATE + INTERVAL '365 days')::DATE,
    TRUE,
    25,
    150,
    '["ALL_MODULES","MULTI_BRANCH","RBAC","AUDIT","MFA","REVIEWS","GROUP_PROMOS"]',
    TRUE,
    'Licencia Enterprise fundadora para VIP Center Fit.',
    'system@gymvip.pe'
FROM organizations o
WHERE o.id = '11111111-1111-4111-8111-111111111111'
ON CONFLICT (organization_id) DO NOTHING;

INSERT INTO commission_rules (id, organization_id, saas_plan_id, operation_type, commission_type, percentage_rate, fixed_fee_pen, gateway_passthrough_pct, applies_only_settled, is_active, created_by)
VALUES
    ('c1000000-0000-4000-8000-000000000001', NULL, NULL, 'MEMBERSHIP', 'PERCENTAGE', 0.0300, 0.00, 0.0344, TRUE, TRUE, 'system@gymvip.pe'),
    ('c1000000-0000-4000-8000-000000000002', NULL, NULL, 'SUPPLEMENT', 'PERCENTAGE', 0.0500, 0.00, 0.0344, TRUE, TRUE, 'system@gymvip.pe'),
    ('c1000000-0000-4000-8000-000000000003', NULL, NULL, 'GROUP_ENROLLMENT', 'PERCENTAGE', 0.0250, 0.00, 0.0344, TRUE, TRUE, 'system@gymvip.pe')
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_commercial_profiles (organization_id, slug, headline, tagline, about_text, amenities_json, schedule_json, gallery_urls_json, reference_plans_json, contact_whatsapp, instagram_handle, website_url, publish_status, published_at_utc, updated_by)
SELECT
    o.id,
    'vip-center-fit',
    'VIP Center Fit — Entrenamiento Integral & Tecnología Biométrica',
    'Dos sedes en Lima con zonas funcionales, musculación de alta gama y seguimiento digital en tiempo real.',
    'Centro deportivo afiliado a la red GYM VIP. Ofrecemos planes transparentes, control ágil por código QR, clases grupales guiadas y tienda de suplementación verificada.',
    '["Musculación","Zona Funcional","Cardio","Spinning","Clases Grupales","Vestidores & Duchas","Asesoría Nutricional Básica"]',
    '{"weekdays":"06:00 - 22:00","saturday":"07:00 - 20:00","sunday":"08:00 - 15:00"}',
    '[]',
    '[{"name":"Plan Mensual Base","pricePen":139.00,"durationDays":30},{"name":"Plan Trimestral Pro","pricePen":379.00,"durationDays":90}]',
    '+51 905 436 989',
    '@vipcenterfit.pe',
    'https://gymvip.pe',
    'PUBLICADO',
    NOW(),
    'system@gymvip.pe'
FROM organizations o
WHERE o.id = '11111111-1111-4111-8111-111111111111'
ON CONFLICT (organization_id) DO NOTHING;

INSERT INTO group_promotions (id, organization_id, title, description, min_participants, max_participants, discount_percent, valid_from, valid_until, status, terms, created_by)
SELECT
    'd1000000-0000-4000-8000-000000000001',
    o.id,
    'Plan Dúo / Squad Fitness (-15%)',
    'Inscríbete con 1 a 3 compañeros en la misma sede y accede a 15% de descuento por persona en membresía mensual.',
    2,
    4,
    15.00,
    CURRENT_DATE,
    (CURRENT_DATE + INTERVAL '90 days')::DATE,
    'ACTIVA',
    'Todos los integrantes deben aceptar la invitación dentro de 48h y formalizar el pago en sede o mediante pasarela habilitada.',
    'system@gymvip.pe'
FROM organizations o
WHERE o.id = '11111111-1111-4111-8111-111111111111'
ON CONFLICT (id) DO NOTHING;

INSERT INTO legal_document_versions (id, document_code, version, title, summary, content_markdown, effective_date, is_active)
VALUES
    ('e1000000-0000-4000-8000-000000000001', 'PRIVACY_POLICY', 'v2.0-2026',
     'Política de Privacidad GYM VIP SaaS B2B2C (Ley 29733 y D.S. 016-2024-JUS)',
     'Diferencia claramente a GYM VIP como Plataforma SaaS y encargado/responsable tecnológico frente a cada gimnasio afiliado.',
     'GYM VIP trata datos personales conforme a la Ley N° 29733 y su Reglamento D.S. 016-2024-JUS. Cada gimnasio afiliado actúa como responsable de la gestión presencial y contractual de sus socios; GYM VIP provee la infraestructura multiempresa con aislamiento de datos, registro explícito de consentimientos, control granular de notificaciones comerciales (nunca premarcadas) y derecho a ejercer acceso, rectificación, cancelación y oposición (ARCO).',
     CURRENT_DATE, TRUE),
    ('e1000000-0000-4000-8000-000000000002', 'TERMS_OF_SERVICE', 'v2.0-2026',
     'Términos y Condiciones de Uso — Plataforma Multiempresa GYM VIP',
     'Regula el uso del portal web operativo, consola SaaS, explorador público y aplicación móvil KMP.',
     'El uso de GYM VIP distingue tres relaciones jurídicas: (1) Licenciamiento B2B entre GYM VIP y empresas/gimnasios contratantes; (2) Gestión de membresías y servicios deportivos directamente entre el gimnasio y su socio; (3) Uso gratuito de la app móvil para exploración, seguimiento de entrenamiento y membresías contratadas. El perfil físico y el IMC tienen fines exclusivamente informativos y no constituyen diagnóstico ni prescripción médica.',
     CURRENT_DATE, TRUE),
    ('e1000000-0000-4000-8000-000000000003', 'LEGAL_NOTICE', 'v2.0-2026',
     'Aviso Legal y Reglas de Comercio Digital Perú',
     'Información societaria referencial, transparencia de comisiones e independencia de pasarelas externas.',
     'Los cobros en línea se canalizan exclusivamente a través de proveedores autorizados (p. ej. Culqi) sin almacenamiento de datos de tarjeta en GYM VIP. Las reseñas son emitidas por usuarios con vínculo verificable y la información de planes corresponde a lo publicado por cada establecimiento.',
     CURRENT_DATE, TRUE)
ON CONFLICT (document_code, version) DO NOTHING;

INSERT INTO app_releases (id, platform, version_code, version_name, channel, download_url, sha256_checksum, file_size_bytes, release_notes, min_supported_version_code, is_mandatory, is_active)
VALUES
    ('f1000000-0000-4000-8000-000000000001', 'ANDROID', 200, '2.0.0', 'PRODUCTION',
     'https://gymvip.pe/descargas/gymvip-mobile-v2.0.0.apk',
     'pending-release-ci-sha256-calculation',
     18500000,
     'GYM VIP 2.0: Explorador público de gimnasios y comparador, perfil físico e IMC educativo, planificador semanal, bitácora de entrenamiento, canales Push segmentados y promociones grupales.',
     100, FALSE, TRUE)
ON CONFLICT (platform, version_code) DO NOTHING;
