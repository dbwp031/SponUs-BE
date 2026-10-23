-- KEYS: daily viewers, pending count, updated organizations
-- ARGV: viewer ID, next midnight (epoch seconds), organization ID
-- Check types before writing: Lua isolates execution, but does not roll back errors.
local viewersType = redis.call('TYPE', KEYS[1]).ok
local updatedType = redis.call('TYPE', KEYS[3]).ok
if viewersType ~= 'none' and viewersType ~= 'set' then
    return redis.error_reply('WRONGTYPE viewers must be a set')
end
if updatedType ~= 'none' and updatedType ~= 'set' then
    return redis.error_reply('WRONGTYPE updated organizations must be a set')
end

local expiresAt = tonumber(ARGV[2])
if not expiresAt or expiresAt ~= math.floor(expiresAt) then
    return redis.error_reply('ERR invalid viewer expiry')
end
-- A request crossing midnight must retry with the new day's boundary.
if expiresAt <= tonumber(redis.call('TIME')[1]) then
    return redis.error_reply('ERR viewer expiry is in the past')
end

if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
    redis.call('EXPIREAT', KEYS[1], expiresAt)
    return 0
end

-- INCR validates the counter type/range before recording the viewer as counted.
redis.call('INCR', KEYS[2])
redis.call('SADD', KEYS[1], ARGV[1])
redis.call('EXPIREAT', KEYS[1], expiresAt)
redis.call('SADD', KEYS[3], ARGV[3])
return 1
