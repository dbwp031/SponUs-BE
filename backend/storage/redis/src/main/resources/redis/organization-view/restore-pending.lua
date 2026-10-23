-- KEYS: pending count, updated organizations
-- ARGV: consumed count to restore, organization ID
local updatedType = redis.call('TYPE', KEYS[2]).ok
if updatedType ~= 'none' and updatedType ~= 'set' then
    return redis.error_reply('WRONGTYPE updated organizations must be a set')
end

-- Add to any new views accumulated while the failed batch was running.
if ARGV[1] ~= '0' then
    redis.call('INCRBY', KEYS[1], ARGV[1])
end
redis.call('SADD', KEYS[2], ARGV[2])
return 1
