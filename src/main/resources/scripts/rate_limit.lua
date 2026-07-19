-- KEYS[1] = rate limit bucket key, e.g. "ratelimit:hold:<userId>"
-- ARGV[1] = max tokens allowed in the window
-- ARGV[2] = window duration in seconds

local current = tonumber(redis.call("GET", KEYS[1]) or "0")
local maxTokens = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])

if current >= maxTokens then
    return 0  -- rejected: rate limit exceeded
end

if current == 0 then
    -- first request in this window: set count to 1 with expiry
    redis.call("SET", KEYS[1], 1, "EX", windowSeconds)
else
    -- subsequent request: increment without resetting the TTL
    redis.call("INCR", KEYS[1])
end

return 1  -- allowed