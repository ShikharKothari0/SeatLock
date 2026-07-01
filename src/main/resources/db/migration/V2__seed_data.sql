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

-- First 3 Seats are hardcoded with stable UUIDs for testing purposes
INSERT INTO seat (id, event_id, seat_number, section, status, version)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'A1', 'Section A', 'AVAILABLE', 0),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'A2', 'Section A', 'AVAILABLE', 0),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '22222222-2222-2222-2222-222222222222', 'A3', 'Section A', 'AVAILABLE', 0);

-- Generate rest 97 seats (A4–A100) for this event, all AVAILABLE with random UUIDs
INSERT INTO seat (id, event_id, seat_number, section, status, version)
SELECT
    gen_random_uuid(),
    '22222222-2222-2222-2222-222222222222',
    'A' || generate_series(4, 100),
    'Section A',
    'AVAILABLE',
    0;

INSERT INTO app_user (id, email, display_name)
VALUES (
           '33333333-3333-3333-3333-333333333333', --Hardcoded UUID for the test user
           'testuser@seatlock.com',
           'Test User'
       );