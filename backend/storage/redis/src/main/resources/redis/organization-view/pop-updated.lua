-- Snapshot and remove the same set atomically. New registrations run afterwards.
local members = redis.call('SMEMBERS', KEYS[1])
if #members > 0 then
    redis.call('DEL', KEYS[1])
end
return members
