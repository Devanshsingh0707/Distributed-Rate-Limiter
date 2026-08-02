-- KEYS[1] = redis sorted set key
-- ARGV[1] = limit
-- ARGV[2] = window (milliseconds)
-- ARGV[3] = current timestamp (milliseconds)
-- ARGV[4] = unique request id

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local request_id = ARGV[4]

local clear_before = now - window
redis.call("ZREMRANGEBYSCORE", key, "-inf", clear_before)

local current_requests = redis.call("ZCARD", key)

if current_requests + 1 > limit then
  return 0 -- blocked
else
  redis.call("ZADD", key, now, request_id)
  redis.call("EXPIRE", key, math.ceil(window / 1000.0))
  return 1 -- allowed
end
