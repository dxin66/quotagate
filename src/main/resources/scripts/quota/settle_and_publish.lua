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

redis.call('XADD', KEYS[2], '*',
    'requestId', ARGV[3],
    'tenantId', ARGV[4],
    'provider', ARGV[5],
    'inputTokens', ARGV[6],
    'outputTokens', ARGV[7],
    'eventType', ARGV[8])

return 1
