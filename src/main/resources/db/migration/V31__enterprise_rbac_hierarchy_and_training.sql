-- ==============================================================================
-- GYM VIP ENTERPRISE — MIGRACIÓN FLYWAY V31
-- Jerarquía organizacional (Plataforma -> Empresa -> Sedes), RBAC granular,
-- MFA privilegiado, Preferencias de guías, Entrenamientos y Reservas.
-- Estricto carácter aditivo y no destructivo sobre datos existentes.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(30),
    description VARCHAR(500),
    plan_tier VARCHAR(40) NOT NULL DEFAULT 'ENTERPRISE',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(150) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'Lima',
    district VARCHAR(100) NOT NULL DEFAULT 'Miraflores',
    address VARCHAR(250) NOT NULL DEFAULT 'Av. Principal 100',
    phone VARCHAR(30),
    schedule_summary VARCHAR(250),
    services_summary VARCHAR(500),
    capacity INT NOT NULL DEFAULT 120,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_branch_org_code UNIQUE (organization_id, code)
);

CREATE TABLE IF NOT EXISTS account_branch_assignments (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    branch_role VARCHAR(60) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    assigned_by VARCHAR(150),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_account_branch_role UNIQUE (account_id, branch_id, branch_role)
);

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS primary_branch_id UUID;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(120);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS mfa_recovery_hashes VARCHAR(1200);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS mfa_verified_at TIMESTAMP WITHOUT TIME ZONE;

CREATE TABLE IF NOT EXISTS user_preferences (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    show_guides BOOLEAN NOT NULL DEFAULT TRUE,
    experience_mode VARCHAR(20) NOT NULL DEFAULT 'GUIDED',
    preferred_branch_id UUID,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS exercises (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    muscle_group VARCHAR(60) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    difficulty VARCHAR(30) NOT NULL DEFAULT 'INTERMEDIO',
    technique VARCHAR(1000) NOT NULL,
    warnings VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workout_routines (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    title VARCHAR(150) NOT NULL,
    goal VARCHAR(100) NOT NULL,
    level VARCHAR(40) NOT NULL DEFAULT 'INTERMEDIO',
    duration_minutes INT NOT NULL DEFAULT 50,
    trainer_account_id UUID REFERENCES accounts(id),
    assigned_client_id UUID REFERENCES clients(id),
    is_personal BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workout_routine_items (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL REFERENCES workout_routines(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises(id),
    sort_order INT NOT NULL,
    sets_count INT NOT NULL DEFAULT 4,
    reps_target VARCHAR(30) NOT NULL DEFAULT '10-12',
    rest_seconds INT NOT NULL DEFAULT 90,
    trainer_notes VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS workout_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    routine_id UUID REFERENCES workout_routines(id),
    routine_title VARCHAR(150) NOT NULL,
    branch_id UUID REFERENCES branches(id),
    duration_seconds INT NOT NULL DEFAULT 0,
    total_volume_kg NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes VARCHAR(1000),
    client_sync_id VARCHAR(120) UNIQUE,
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS workout_set_logs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id UUID,
    exercise_name VARCHAR(120) NOT NULL,
    set_number INT NOT NULL,
    reps_completed INT NOT NULL,
    weight_kg NUMERIC(8,2) NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS gym_classes (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    branch_id UUID REFERENCES branches(id),
    title VARCHAR(140) NOT NULL,
    category VARCHAR(60) NOT NULL,
    trainer_name VARCHAR(120) NOT NULL,
    trainer_account_id UUID REFERENCES accounts(id),
    starts_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    capacity INT NOT NULL DEFAULT 20,
    status VARCHAR(30) NOT NULL DEFAULT 'PROGRAMADA'
);

CREATE TABLE IF NOT EXISTS class_reservations (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES gym_classes(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVADA',
    reserved_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMP WITHOUT TIME ZONE
);

INSERT INTO organizations (id, code, name, tax_id, description, plan_tier, status, is_published)
VALUES ('11111111-1111-4111-8111-111111111111', 'vip-center-fit', 'VIP Center Fit (GYM VIP)', '20600000001',
        'Centro de entrenamiento integral con control biométrico, zonas funcionales, musculación y asesoría profesional.',
        'ENTERPRISE', 'ACTIVO', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO branches (id, organization_id, code, name, city, district, address, phone, schedule_summary, services_summary, capacity, is_published, status)
VALUES
    ('22222222-2222-4222-8222-222222222221', '11111111-1111-4111-8111-111111111111', 'sede-central',
     'VIP Center Fit — Sede Central', 'Lima', 'Miraflores', 'Av. José Larco 740', '+51 905 436 989',
     'Lun-Sáb 06:00 - 22:00 | Dom 08:00 - 15:00',
     'Musculación, Cardio, Zona Funcional, Entrenamiento Personal, Suplementación',
     140, TRUE, 'ACTIVO'),
    ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'sede-san-borja',
     'VIP Center Fit — Sede San Borja', 'Lima', 'San Borja', 'Av. Aviación 2480', '+51 905 436 990',
     'Lun-Sáb 06:00 - 22:00 | Dom 08:00 - 14:00',
     'Musculación, Spinning, Yoga, Clases Grupales, Evaluación Física',
     110, TRUE, 'ACTIVO')
ON CONFLICT (organization_id, code) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, difficulty, technique, warnings)
VALUES
    ('33333333-3333-4333-8333-333333333301', 'Sentadilla Libre con Barra', 'Piernas / Cuádriceps', 'Barra y Rack', 'INTERMEDIO',
     'Pies al ancho de hombros, pecho erguido, desciende controlando rodillas alineadas con punteras y empuja desde el centro del pie.',
     'Mantén zona lumbar neutra; usa topes de seguridad si entrenas solo.'),
    ('33333333-3333-4333-8333-333333333302', 'Press Banca Plano', 'Pecho / Tríceps', 'Barra y Banco Plano', 'INTERMEDIO',
     'Retrae escápulas, agarra la barra ligeramente más ancho que los hombros, baja hacia el tercio medio del esternón y empuja estable.',
     'Evita rebotar la barra en el pecho y usa seguro o spotter en cargas altas.'),
    ('33333333-3333-4333-8333-333333333303', 'Peso Muerto Rumano', 'Isquios / Glúteos', 'Barra o Mancuernas', 'INTERMEDIO',
     'Desbloquea ligeramente las rodillas y lleva la cadera hacia atrás manteniendo la barra pegada a los muslos hasta sentir tensión posterior.',
     'No redondees la espalda baja; controla la carga.'),
    ('33333333-3333-4333-8333-333333333304', 'Jalón al Pecho en Polea', 'Espalda / Dorsal', 'Polea Alta', 'PRINCIPIANTE',
     'Sujeta la barra con agarre prono amplio, lleva el pecho arriba y tracciona los codos hacia abajo hasta la parte superior del pecho.',
     'Evita balancear el torso bruscamente hacia atrás.'),
    ('33333333-3333-4333-8333-333333333305', 'Press Militar con Mancuernas', 'Hombros / Core', 'Mancuernas', 'PRINCIPIANTE',
     'Activa abdomen y glúteos, eleva mancuernas sobre la cabeza alineando codos ligeramente al frente sin hiperextender la zona lumbar.',
     'Controla el descenso hasta la altura de orejas.'),
    ('33333333-3333-4333-8333-333333333306', 'Remo con Mancuerna a Una Mano', 'Espalda Media / Bíceps', 'Mancuerna y Banco', 'PRINCIPIANTE',
     'Apoya rodilla y mano libre en banco manteniendo espalda paralela al suelo y lleva la mancuerna hacia la cadera.',
     'No rotes el tronco para compensar peso.'),
    ('33333333-3333-4333-8333-333333333307', 'Zancadas Caminando', 'Piernas / Glúteos', 'Peso Corporal / Mancuernas', 'PRINCIPIANTE',
     'Da un paso largo al frente, desciende verticalmente hasta 90 grados en ambas rodillas y empuja con la pierna adelantada.',
     'Cuida que la rodilla delantera no colapse hacia adentro.'),
    ('33333333-3333-4333-8333-333333333308', 'Plancha Abdominal Isométrica', 'Core / Abdomen', 'Colchoneta', 'PRINCIPIANTE',
     'Antebrazos bajo hombros, glúteos y abdomen contraídos formando una línea recta de cabeza a talones.',
     'Detén la serie si sientes molestia lumbar por caída de cadera.')
ON CONFLICT (name) DO NOTHING;

INSERT INTO workout_routines (id, organization_id, branch_id, title, goal, level, duration_minutes, is_personal, notes, status)
VALUES
    ('44444444-4444-4444-8444-444444444401', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222221',
     'Fuerza Base Full Body (Día A)', 'Fuerza e Hipertrofia General', 'INTERMEDIO', 55, FALSE,
     'Rutina estructurada de cuerpo completo recomendada por piso para sesiones de 3 días por semana.', 'ACTIVO'),
    ('44444444-4444-4444-8444-444444444402', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222221',
     'Acondicionamiento Tren Inferior & Core', 'Tonificación y Resistencia Muscular', 'PRINCIPIANTE', 45, FALSE,
     'Enfoque guiado en técnica segura, estabilidad de cadera y fortalecimiento del core.', 'ACTIVO')
ON CONFLICT (id) DO NOTHING;

INSERT INTO workout_routine_items (id, routine_id, exercise_id, sort_order, sets_count, reps_target, rest_seconds, trainer_notes)
VALUES
    ('45454545-4444-4444-8444-444444444401', '44444444-4444-4444-8444-444444444401', '33333333-3333-4333-8333-333333333301', 1, 4, '8-10', 120, 'Calentar 2 series progresivas antes del peso efectivo.'),
    ('45454545-4444-4444-8444-444444444402', '44444444-4444-4444-8444-444444444401', '33333333-3333-4333-8333-333333333302', 2, 4, '8-10', 90, 'Pausa breve de 1s cerca del pecho.'),
    ('45454545-4444-4444-8444-444444444403', '44444444-4444-4444-8444-444444444401', '33333333-3333-4333-8333-333333333304', 3, 3, '10-12', 75, 'Controlar 2 segundos en la fase de subida.'),
    ('45454545-4444-4444-8444-444444444404', '44444444-4444-4444-8444-444444444401', '33333333-3333-4333-8333-333333333305', 4, 3, '10-12', 75, 'Abdomen firme en cada repetición.'),
    ('45454545-4444-4444-8444-444444444405', '44444444-4444-4444-8444-444444444402', '33333333-3333-4333-8333-333333333303', 1, 3, '10-12', 90, 'Bisagra de cadera controlada.'),
    ('45454545-4444-4444-8444-444444444406', '44444444-4444-4444-8444-444444444402', '33333333-3333-4333-8333-333333333307', 2, 3, '12 c/pierna', 60, 'Paso firme y postura vertical.'),
    ('45454545-4444-4444-8444-444444444407', '44444444-4444-4444-8444-444444444402', '33333333-3333-4333-8333-333333333308', 3, 3, '45s', 45, 'Respiración continua sin arquear espalda.')
ON CONFLICT (id) DO NOTHING;

INSERT INTO gym_classes (id, organization_id, branch_id, title, category, trainer_name, starts_at, duration_minutes, capacity, status)
VALUES
    ('55555555-5555-4555-8555-555555555501', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222221',
     'Funcional HIIT & Core', 'Funcional', 'Coach Carlos Mendoza', '2026-11-28 19:00:00', 50, 20, 'PROGRAMADA'),
    ('55555555-5555-4555-8555-555555555502', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222221',
     'Spinning Resistencia Nocturna', 'Cardio', 'Coach Andrea Paredes', '2026-11-29 20:00:00', 45, 25, 'PROGRAMADA'),
    ('55555555-5555-4555-8555-555555555503', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222',
     'Movilidad, Fuerza y Yoga', 'Movilidad', 'Coach Valeria Quispe', '2026-11-30 09:00:00', 60, 18, 'PROGRAMADA')
ON CONFLICT (id) DO NOTHING;
