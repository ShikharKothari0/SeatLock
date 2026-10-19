ALTER TABLE venue ADD COLUMN address TEXT NOT NULL DEFAULT '';
ALTER TABLE venue ADD COLUMN timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Kolkata';
CREATE TABLE venue_seat (
 id UUID PRIMARY KEY, venue_id UUID NOT NULL REFERENCES venue(id),
 seat_number VARCHAR(20) NOT NULL, section VARCHAR(50) NOT NULL,
 UNIQUE(venue_id,seat_number)
);
ALTER TABLE event ADD COLUMN publication VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
 CHECK(publication IN ('DRAFT','PUBLISHED','CLOSED'));
ALTER TABLE event ADD COLUMN sale_end_time TIMESTAMPTZ;
ALTER TABLE event ADD COLUMN fee_minor BIGINT NOT NULL DEFAULT 0 CHECK(fee_minor BETWEEN 0 AND 100000000);
CREATE TABLE event_price (
 event_id UUID REFERENCES event(id), section VARCHAR(50),
 amount_minor BIGINT NOT NULL CHECK(amount_minor BETWEEN 0 AND 100000000),
 PRIMARY KEY(event_id,section)
);
-- Explicit demo price for pre-migration inventory only. New events require administrator pricing.
INSERT INTO event_price SELECT DISTINCT event_id,section,50000 FROM seat;
CREATE TABLE checkout_quote (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), event_id UUID NOT NULL REFERENCES event(id),
 expires_at TIMESTAMPTZ NOT NULL, snapshot TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX checkout_quote_owner ON checkout_quote(user_id,created_at);
CREATE TABLE booking_price (
 booking_id UUID PRIMARY KEY REFERENCES booking(id), quote_id UUID NOT NULL UNIQUE REFERENCES checkout_quote(id), snapshot TEXT NOT NULL
);
CREATE TABLE catalog_audit (
 id UUID PRIMARY KEY, actor_id UUID NOT NULL REFERENCES app_user(id), action VARCHAR(50) NOT NULL,
 resource_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
