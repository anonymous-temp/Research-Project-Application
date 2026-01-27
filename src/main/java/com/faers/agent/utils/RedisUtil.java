package com.faers.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * RedisUtil - Proper Spring-managed Redis utility service
 *
 * This class provides a properly designed Redis utility that follows Spring's
 * dependency injection principles. It should be injected via @Autowired where needed.
 *
 * Key improvements over previous implementation:
 * 1. No static fields - proper instance management by Spring
 * 2. Proper constructor injection for better testability
 * 3. Thread-safe by design through Spring's singleton scope
 * 4. Comprehensive error handling
 */
@Service
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Constructor injection for better testability and clear dependencies
     */
    public RedisUtil(RedisTemplate<String, Object> redisTemplate,
                     StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        log.info("RedisUtil initialized with proper dependency injection");
    }

    /**
     * Get RedisTemplate for advanced operations
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * Get StringRedisTemplate for string operations
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }

    // ==================== Common Operations ====================

    /**
     * Set expiration time for a key
     */
    public boolean expire(String key, long time, TimeUnit timeUnit) {
        try {
            if (time > 0) {
                return Boolean.TRUE.equals(redisTemplate.expire(key, time, timeUnit));
            }
            return false;
        } catch (Exception e) {
            log.error("Redis expire error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Get expiration time for a key
     */
    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return expire != null ? expire : -1;
        } catch (Exception e) {
            log.error("Redis getExpire error | key={} | error={}", key, e.getMessage());
            return -1;
        }
    }

    /**
     * Check if key exists
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis hasKey error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Delete keys
     */
    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            try {
                if (keys.length == 1) {
                    redisTemplate.delete(keys[0]);
                } else {
                    redisTemplate.delete(List.of(keys));
                }
            } catch (Exception e) {
                log.error("Redis delete error | keys={} | error={}", keys, e.getMessage());
            }
        }
    }

    // ==================== String Operations ====================

    /**
     * Get string value
     */
    public Object get(String key) {
        try {
            return key == null ? null : redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get error | key={} | error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Get string value as String
     */
    public String getString(String key) {
        try {
            return key == null ? null : stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis getString error | key={} | error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Set string value
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("Redis set error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Set string value with expiration
     */
    public boolean set(String key, Object value, long time, TimeUnit timeUnit) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, timeUnit);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis set with expire error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Set string value (String type)
     */
    public boolean setString(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("Redis setString error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Set string value with expiration (String type)
     */
    public boolean setString(String key, String value, long time, TimeUnit timeUnit) {
        try {
            if (time > 0) {
                stringRedisTemplate.opsForValue().set(key, value, time, timeUnit);
            } else {
                stringRedisTemplate.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setString with expire error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Increment value
     */
    public long increment(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis increment error | key={} | delta={} | error={}", key, delta, e.getMessage());
            return 0;
        }
    }

    // ==================== Hash Operations ====================

    /**
     * Get hash field value
     */
    public Object hGet(String key, String field) {
        try {
            return redisTemplate.opsForHash().get(key, field);
        } catch (Exception e) {
            log.error("Redis hGet error | key={} | field={} | error={}", key, field, e.getMessage());
            return null;
        }
    }

    /**
     * Get all hash entries
     */
    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("Redis hGetAll error | key={} | error={}", key, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Set hash field value
     */
    public boolean hSet(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            return true;
        } catch (Exception e) {
            log.error("Redis hSet error | key={} | field={} | error={}", key, field, e.getMessage());
            return false;
        }
    }

    /**
     * Set multiple hash fields
     */
    public boolean hSetAll(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("Redis hSetAll error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Delete hash fields
     */
    public long hDelete(String key, Object... fields) {
        try {
            return redisTemplate.opsForHash().delete(key, fields);
        } catch (Exception e) {
            log.error("Redis hDelete error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Check if hash field exists
     */
    public boolean hHasKey(String key, String field) {
        try {
            return redisTemplate.opsForHash().hasKey(key, field);
        } catch (Exception e) {
            log.error("Redis hHasKey error | key={} | field={} | error={}", key, field, e.getMessage());
            return false;
        }
    }

    // ==================== Set Operations ====================

    /**
     * Get set members
     */
    public Set<Object> sMembers(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Redis sMembers error | key={} | error={}", key, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Check if member exists in set
     */
    public boolean sIsMember(String key, Object value) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            log.error("Redis sIsMember error | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Add members to set
     */
    public long sAdd(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().add(key, values);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis sAdd error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Get set size
     */
    public long sSize(String key) {
        try {
            Long result = redisTemplate.opsForSet().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis sSize error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Remove members from set
     */
    public long sRemove(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().remove(key, values);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis sRemove error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    // ==================== List Operations ====================

    /**
     * Get list range
     */
    public List<Object> lRange(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("Redis lRange error | key={} | error={}", key, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get list size
     */
    public long lSize(String key) {
        try {
            Long result = redisTemplate.opsForList().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis lSize error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Get list element by index
     */
    public Object lIndex(String key, long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            log.error("Redis lIndex error | key={} | index={} | error={}", key, index, e.getMessage());
            return null;
        }
    }

    /**
     * Push to list right
     */
    public long lRightPush(String key, Object value) {
        try {
            Long result = redisTemplate.opsForList().rightPush(key, value);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis lRightPush error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Push to list left
     */
    public long lLeftPush(String key, Object value) {
        try {
            Long result = redisTemplate.opsForList().leftPush(key, value);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis lLeftPush error | key={} | error={}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Pop from list left
     */
    public Object lLeftPop(String key) {
        try {
            return redisTemplate.opsForList().leftPop(key);
        } catch (Exception e) {
            log.error("Redis lLeftPop error | key={} | error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Pop from list right
     */
    public Object lRightPop(String key) {
        try {
            return redisTemplate.opsForList().rightPop(key);
        } catch (Exception e) {
            log.error("Redis lRightPop error | key={} | error={}", key, e.getMessage());
            return null;
        }
    }
}
