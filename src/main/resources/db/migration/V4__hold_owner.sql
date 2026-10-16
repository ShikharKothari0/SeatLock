ALTER TABLE seat ADD COLUMN held_by UUID;
CREATE INDEX seat_active_owner_idx ON seat (held_by, hold_expires_at) WHERE status = 'HELD';
