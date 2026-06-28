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

-- Generate 100 seats (A1–A100) for this event, all AVAILABLE
INSERT INTO seat (id, event_id, seat_number, section, status, version)
SELECT
    gen_random_uuid(),
    '22222222-2222-2222-2222-222222222222',
    'A' || generate_series(1, 100),
    'Section A',
    'AVAILABLE',
    0;