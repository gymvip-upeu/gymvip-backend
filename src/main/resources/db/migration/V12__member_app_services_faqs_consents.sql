-- V12: Soporte para FAQs, Servicios del Gimnasio y Consentimientos Legales/Avisos del Socio (HU-05, HU-15, HU-28, HU-33, HU-34)

CREATE TABLE IF NOT EXISTS member_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE,
    document_version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    accepted_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_consent UNIQUE (client_id)
);

CREATE INDEX IF NOT EXISTS idx_member_consents_client ON member_consents(client_id);

CREATE TABLE IF NOT EXISTS member_inbox_reads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    item_id VARCHAR(100) NOT NULL,
    read_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_inbox_read UNIQUE (client_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_member_inbox_reads_client ON member_inbox_reads(client_id);

CREATE TABLE IF NOT EXISTS gym_faqs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question VARCHAR(300) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gym_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NOT NULL,
    schedule VARCHAR(150) NOT NULL,
    location VARCHAR(100) NOT NULL,
    is_included BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Inserción de FAQs oficiales de GYM VIP
INSERT INTO gym_faqs (id, question, answer, category, display_order, is_active)
VALUES
    ('b0000001-0000-0000-0000-000000000001', '¿Cómo funciona la vigencia de mi membresía mensual?', 'La membresía mensual tiene una duración estricta de 30 días calendario continuos desde su inicio, con cero tolerancia de días de gracia.', 'Membresías', 1, true),
    ('b0000001-0000-0000-0000-000000000002', '¿Puedo pagar o renovar mi plan desde la aplicación móvil?', 'No. Por política de seguridad y operación autorizada, todo pago y renovación se efectúa exclusivamente en el mostrador de recepción.', 'Pagos', 2, true),
    ('b0000001-0000-0000-0000-000000000003', '¿Puedo ver mis pagos anteriores si mi membresía ya venció?', 'Sí. Su historial de pagos y perfil permanecen accesibles en la app mientras su cuenta de socio se mantenga habilitada.', 'Historial', 3, true),
    ('b0000001-0000-0000-0000-000000000004', '¿Qué horarios de atención tiene el gimnasio?', 'Lunes a viernes de 6:00 a.m. a 10:00 p.m., sábados de 7:00 a.m. a 8:00 p.m. y domingos de 8:00 a.m. a 2:00 p.m.', 'Instalaciones', 4, true),
    ('b0000001-0000-0000-0000-000000000005', '¿Cómo solicito mi activación si es mi primera vez?', 'Tras registrarse en recepción, el personal le entregará un comprobante con su código de activación presencial para configurar su contraseña personal.', 'Cuenta', 5, true),
    ('b0000001-0000-0000-0000-000000000006', '¿Cómo accedo a los suplementos y promociones?', 'Consulte la sección Catálogo en la app móvil. Todas las compras y canjes se concretan directamente en recepción.', 'Catálogo', 6, true)
ON CONFLICT DO NOTHING;

-- Inserción de Servicios oficiales de GYM VIP
INSERT INTO gym_services (id, name, description, schedule, location, is_included, display_order)
VALUES
    ('c0000001-0000-0000-0000-000000000001', 'Musculación y Peso Libre', 'Equipamiento de alta gama con barras olímpicas, mancuernas hasta 50kg y máquinas de placas selectorizadas.', 'Lunes a Domingo (horario regular)', 'Piso 1 - Sala Principal', true, 1),
    ('c0000001-0000-0000-0000-000000000002', 'Zona Cardiovascular', 'Cintas de correr de última generación, elípticas, bicicletas de spinning y remos con monitoreo de ritmo cardíaco.', 'Lunes a Domingo (horario regular)', 'Piso 2 - Sala Cardio', true, 2),
    ('c0000001-0000-0000-0000-000000000003', 'Clases Grupales VIP', 'Sesiones guiadas de funcional, spinning, yoga y pilates incluidas según cupo y disponibilidad diaria.', 'L-V 7:00 - 9:00 y 18:00 - 21:00', 'Piso 2 - Sala Multiusos', true, 3),
    ('c0000001-0000-0000-0000-000000000004', 'Lockers y Vestuarios', 'Casilleros de uso diario con llave personal, duchas con agua caliente y área de cambiadores climatizada.', 'Lunes a Domingo (horario regular)', 'Planta Baja - Vestuarios', true, 4),
    ('c0000001-0000-0000-0000-000000000005', 'Asesoría Nutricional Básica', 'Orientación y recomendaciones de suplementación deportiva aprobada por especialistas autorizados.', 'Martes y Jueves 16:00 - 20:00', 'Consultorio VIP', true, 5),
    ('c0000001-0000-0000-0000-000000000006', 'Sauna Seco', 'Zona de recuperación térmica e higiene postural para miembros con plan activo.', 'Viernes a Domingo 14:00 - 20:00', 'Área de Bienestar', true, 6)
ON CONFLICT DO NOTHING;

