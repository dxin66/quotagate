local limit = tonumber(redis.call('HGET', KEYS[1], 'limit') or '-1')
local used = tonumber(redis.call('HGET', KEYS[1], 'used') or '0')
local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved') or '0')
local requested = tonumber(ARGV[1])

if limit < 0 or requested <= 0 then
    return -1
end

if used + reserved + requested > limit then
    return 0
end

redis.call('HSET', KEYS[1], 'used', used, 'reserved', reserved + requested)
return 1