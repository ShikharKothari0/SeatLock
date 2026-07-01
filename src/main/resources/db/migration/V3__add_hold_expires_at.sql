ALTER TABLE seat
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

CREATE INDEX idx_seat_held_expires
    ON seat(status, hold_expires_at)
    WHERE status = 'HELD';