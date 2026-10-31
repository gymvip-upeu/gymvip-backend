-- Legacy cancelled contracts must not regain their old coverage when a replacement is assigned.
UPDATE membership_periods p SET cancelled_at_utc=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), cancellation_reason='Contrato cancelado antes de la actualización'
FROM memberships m WHERE p.membership_id=m.id AND m.status='CANCELADA';
ALTER TABLE membership_periods DROP CONSTRAINT uk_period_start;
CREATE UNIQUE INDEX uk_active_period_start ON membership_periods(membership_id,start_date) WHERE cancelled_at_utc IS NULL;
