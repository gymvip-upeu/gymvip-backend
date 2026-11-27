ALTER TABLE exercises DROP CONSTRAINT IF EXISTS exercises_name_key;
ALTER TABLE exercises
    ADD COLUMN organization_id UUID REFERENCES organizations(id),
    ADD COLUMN branch_id UUID REFERENCES branches(id),
    ADD COLUMN image_url VARCHAR(1000),
    ADD COLUMN video_url VARCHAR(1000);
CREATE UNIQUE INDEX ux_exercises_shared_name ON exercises (lower(name)) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX ux_exercises_branch_name ON exercises (organization_id, branch_id, lower(name))
    WHERE organization_id IS NOT NULL AND is_active;

CREATE TABLE exercise_drafts (
    exercise_id UUID PRIMARY KEY REFERENCES exercises(id) ON DELETE CASCADE,
    content JSONB NOT NULL,
    updated_by UUID REFERENCES accounts(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Legacy retries could leave more than one open reservation. Retain the earliest confirmed record.
WITH ranked AS (
    SELECT id, row_number() OVER (
        PARTITION BY class_id, client_id ORDER BY (status = 'RESERVADA') DESC, reserved_at, id
    ) AS sequence
    FROM class_reservations WHERE status IN ('RESERVADA', 'LISTA_ESPERA')
)
UPDATE class_reservations r SET status = 'CANCELADA', cancelled_at = CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima'
FROM ranked WHERE ranked.id = r.id AND ranked.sequence > 1;

CREATE UNIQUE INDEX ux_class_reservations_open ON class_reservations (class_id, client_id)
    WHERE status IN ('RESERVADA', 'LISTA_ESPERA');
CREATE INDEX idx_class_reservations_waitlist ON class_reservations (class_id, reserved_at, id)
    WHERE status = 'LISTA_ESPERA';

CREATE TABLE class_reservation_events (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES class_reservations(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('RESERVADA', 'LISTA_ESPERA', 'PROMOVIDA', 'CANCELADA')),
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, event_type)
);

CREATE TABLE inbox_archives (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    item_id VARCHAR(150) NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, item_id)
);

ALTER TABLE gym_announcements ADD COLUMN organization_id UUID REFERENCES organizations(id);
UPDATE gym_announcements n SET organization_id = COALESCE(
    (SELECT b.organization_id FROM gym_services s JOIN branches b ON b.id=s.branch_id WHERE s.id=n.facility_id),
    (SELECT a.organization_id FROM accounts a WHERE LOWER(a.email)=LOWER(n.created_by) LIMIT 1),
    '11111111-1111-4111-8111-111111111111'
);
ALTER TABLE gym_announcements
    ALTER COLUMN organization_id SET DEFAULT '11111111-1111-4111-8111-111111111111',
    ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX idx_gym_announcements_org_active ON gym_announcements (organization_id, created_at_utc DESC)
    WHERE is_active;
