ALTER TABLE seat ADD COLUMN hold_token UUID;
ALTER TABLE event ADD COLUMN seat_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE reservation_outbox (
    id UUID PRIMARY KEY,
    kind VARCHAR(30) NOT NULL,
    destination TEXT NOT NULL,
    message_key TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(500)
);
CREATE INDEX reservation_outbox_pending ON reservation_outbox (available_at, created_at) WHERE delivered_at IS NULL;

CREATE TABLE consumed_message (
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, message_id)
);
