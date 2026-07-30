-- All hardcoded values for testing purposes only.

INSERT INTO venue (id, name, city, total_capacity)
VALUES ('11111111-1111-1111-1111-111111111111', 'Test Arena', 'Mumbai', 100);

INSERT INTO event (id, name, venue_id, sale_start_time, event_time)
VALUES (
           '22222222-2222-2222-2222-222222222222',
           'Test Concert',
           '11111111-1111-1111-1111-111111111111',
           NOW(),
           NOW() + INTERVAL '7 days'
       );

-- Generate 100 seats with deterministic UUIDs
INSERT INTO seat (id, event_id, seat_number, section, status, version)
SELECT
    (
        LPAD(gs::text, 8, '0') ||
        '-0000-0000-0000-000000000000'
        )::uuid,
    '22222222-2222-2222-2222-222222222222',
    'A' || gs,
    'Section A',
    'AVAILABLE',
    0
FROM generate_series(1, 100) AS gs;

INSERT INTO app_user (id, email, display_name)
VALUES (
           '33333333-3333-3333-3333-333333333333', --Hardcoded UUID for the test user
           'testuser@seatlock.com',
           'Test User'
       );