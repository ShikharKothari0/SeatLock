-- KEYS[1] = rate limit bucket key
-- ARGV[1] = max requests allowed in the window
-- ARGV[2] = window duration in seconds

local count = redis.call("INCR", KEYS[1])

if count == 1 then
    redis.call("EXPIRE", KEYS[1], tonumber(ARGV[2]))
end

if count > tonumber(ARGV[1]) then
    return 0  -- rejected
else
    return 1  -- allowed
end