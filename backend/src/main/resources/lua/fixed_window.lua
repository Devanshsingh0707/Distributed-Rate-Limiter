-- KEYS[1] = redis key
-- ARGV[1] = limit
-- ARGV[2] = window (seconds)

local current = tonumber(redis.call("GET", KEYS[1]) or "0")

if current + 1 > tonumber(ARGV[1]) then
  return 0 -- blocked
else
  local val = redis.call("INCR", KEYS[1])
  if val == 1 then
    redis.call("EXPIRE", KEYS[1], ARGV[2])
  end
  return 1 -- allowed
end
