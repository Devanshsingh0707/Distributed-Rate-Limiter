-- KEYS[1] = redis key
-- ARGV[1] = capacity
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = requested (usually 1)
-- ARGV[4] = current timestamp (milliseconds)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- Retrieve current tokens and last refill timestamp
local data = redis.call("HMGET", key, "tokens", "last_refill")
local tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

if not tokens then
  -- First request, initialize bucket to full capacity
  tokens = capacity
  last_refill = now
else
  -- Calculate time elapsed in seconds and refill tokens
  local elapsed = math.max(0, now - last_refill) / 1000.0
  tokens = math.min(capacity, tokens + (elapsed * refill_rate))
  last_refill = now
end

if tokens < requested then
  -- Blocked, update state but do not deduct tokens
  redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
  -- TTL set to refill the full bucket
  local ttl = math.ceil(capacity / refill_rate)
  redis.call("EXPIRE", key, ttl)
  return 0
else
  -- Allowed, deduct tokens and update state
  tokens = tokens - requested
  redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
  local ttl = math.ceil(capacity / refill_rate)
  redis.call("EXPIRE", key, ttl)
  return 1
end
