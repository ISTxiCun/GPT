-- One Redis key: window begins at first accepted request, using Redis TTL as the clock.
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if current >= limit then
    local ttl = redis.call('PTTL', KEYS[1])
    if ttl < 0 then
        redis.call('PEXPIRE', KEYS[1], window)
        ttl = window
    end
    return {0, math.max(1, math.ceil(ttl / 1000))}
end
local count = redis.call('INCR', KEYS[1])
if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then
    redis.call('PEXPIRE', KEYS[1], window)
end
return {1, 0}
