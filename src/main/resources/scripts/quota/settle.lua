local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved') or '0')
local used = tonumber(redis.call('HGET', KEYS[1], 'used') or '0')
local estimated = tonumber(ARGV[1])
local actual = tonumber(ARGV[2])

if estimated <= 0 or actual < 0 or reserved < estimated then
    return 0
end

redis.call('HSET', KEYS[1],
    'reserved', reserved - estimated,
    'used', used + actual)
return 1