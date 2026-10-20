CREATE TABLE venue_section (
    venue_id UUID NOT NULL REFERENCES venue(id),
    section VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    seats_per_row INTEGER NOT NULL DEFAULT 10 CHECK (seats_per_row BETWEEN 1 AND 20),
    centre_aisle BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (venue_id, section)
);
CREATE TABLE event_section (
    event_id UUID NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    section VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    seats_per_row INTEGER NOT NULL DEFAULT 10 CHECK (seats_per_row BETWEEN 1 AND 20),
    centre_aisle BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (event_id, section)
);
-- Preserve every existing seat and booking. Legacy sections have no physical
-- ordering recorded: use a deterministic alphabetical order, not price rank.
INSERT INTO venue_section (venue_id, section, display_order)
SELECT venue_id, section, (row_number() OVER (PARTITION BY venue_id ORDER BY section)-1)::integer
FROM (SELECT DISTINCT venue_id, section FROM venue_seat) s;
INSERT INTO event_section (event_id, section, display_order)
SELECT event_id, section, (row_number() OVER (PARTITION BY event_id ORDER BY section)-1)::integer
FROM (SELECT DISTINCT event_id, section FROM seat) s;
