-- A push token is a device address, not proof of ownership. Bind it to a live session.
ALTER TABLE member_device_tokens ADD COLUMN session_token VARCHAR(128) REFERENCES sessions(token) ON DELETE SET NULL;
UPDATE member_device_tokens SET is_active=false;
CREATE UNIQUE INDEX uq_active_push_token ON member_device_tokens(device_token) WHERE is_active=true;
ALTER TABLE notification_outbox ADD COLUMN inbox_item_id VARCHAR(150);
ALTER TABLE notification_outbox ADD COLUMN owner_client_id UUID REFERENCES clients(id) ON DELETE CASCADE;
ALTER TABLE notification_outbox ADD COLUMN binding_session_token VARCHAR(128);
ALTER TABLE notification_outbox ADD COLUMN provider_message_id VARCHAR(250);
ALTER TABLE notification_outbox ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
-- Legacy messages have no safe destination. They must never open an arbitrary screen.
UPDATE notification_outbox SET status='SKIPPED', error_message='Aviso anterior sin destino verificable', processed_at_utc=CURRENT_TIMESTAMP
WHERE channel='PUSH' AND status='PENDING';
