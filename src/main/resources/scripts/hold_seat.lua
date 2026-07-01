-- KEYS[1] = the seat lock key, e.g. "seat:lock:<seatId>"
-- ARGV[1] = the userId requesting the hold
-- ARGV[2] = TTL in seconds

local existing = redis.call("GET", KEYS[1])

if existing then
    return 0
else
    redis.call("SET", KEYS[1], ARGV[1], "EX", ARGV[2])
    return 1
end
