CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE venue (
                       id              UUID PRIMARY KEY,
                       name            VARCHAR(255) NOT NULL,
                       city            VARCHAR(255) NOT NULL,
                       total_capacity  INTEGER NOT NULL
);

CREATE TABLE app_user (
                          id            UUID PRIMARY KEY,
                          email         VARCHAR(255) NOT NULL UNIQUE,
                          display_name  VARCHAR(255) NOT NULL
);

CREATE TABLE event (
                       id              UUID PRIMARY KEY,
                       name            VARCHAR(255) NOT NULL,
                       venue_id        UUID NOT NULL REFERENCES venue(id),
                       sale_start_time TIMESTAMPTZ NOT NULL,
                       event_time      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_event_venue_id ON event(venue_id);

CREATE TABLE booking (
                         id               UUID PRIMARY KEY,
                         user_id          UUID NOT NULL REFERENCES app_user(id),
                         event_id         UUID NOT NULL REFERENCES event(id),
                         status           VARCHAR(20) NOT NULL,
                         created_at       TIMESTAMPTZ NOT NULL,
                         idempotency_key  VARCHAR(255) UNIQUE
);

CREATE INDEX idx_booking_user_id ON booking(user_id);
CREATE INDEX idx_booking_event_id ON booking(event_id);

CREATE TABLE seat (
                      id           UUID PRIMARY KEY,
                      event_id     UUID NOT NULL REFERENCES event(id),
                      seat_number  VARCHAR(20) NOT NULL,
                      section      VARCHAR(50) NOT NULL,
                      status       VARCHAR(20) NOT NULL,
                      booking_id   UUID REFERENCES booking(id),
                      version      BIGINT NOT NULL DEFAULT 0,
                      CONSTRAINT uq_seat_event_seat_number UNIQUE (event_id, seat_number)
);

CREATE INDEX idx_seat_event_id ON seat(event_id);
CREATE INDEX idx_seat_booking_id ON seat(booking_id);
CREATE INDEX idx_seat_status ON seat(status);