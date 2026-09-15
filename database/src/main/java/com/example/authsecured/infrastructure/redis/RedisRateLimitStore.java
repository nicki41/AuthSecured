package com.example.authsecured.infrastructure.redis;

import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.RateLimitStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RedisRateLimitStore implements RateLimitStore {

    private final JedisPool jedisPool;
    private final MemoryRateLimitStore fallbackStore;
    private final PlatformLogger logger;
    private final Executor executor;

    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger) {
        this(jedisPool, logger, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger, Executor executor) {
        this.jedisPool = jedisPool;
        this.fallbackStore = new MemoryRateLimitStore();
        this.logger = logger;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        if (jedisPool == null) {
            return fallbackStore.incrementAndGet(key, windowSeconds);
        }
        return withFallback(CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                long count = jedis.incr(key);
                if (count == 1) {
                    jedis.expire(key, windowSeconds);
                }
                return (int) count;
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.incrementAndGet(key, windowSeconds));
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        if (jedisPool == null) {
            return fallbackStore.isLocked(lockKey);
        }
        return withFallback(CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(lockKey);
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.isLocked(lockKey));
    }

    @Override
    public CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        if (jedisPool == null) {
            return fallbackStore.setLock(lockKey, lockSeconds);
        }
        return withFallback(CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(lockKey, lockSeconds, "LOCKED");
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.setLock(lockKey, lockSeconds));
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        if (jedisPool == null) {
            return fallbackStore.reset(key);
        }
        return withFallback(CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.reset(key));
    }

    private <T> CompletableFuture<T> withFallback(CompletableFuture<T> operation,
                                                   Supplier<CompletableFuture<T>> fallback) {
        return operation.orTimeout(5, TimeUnit.SECONDS).exceptionallyCompose(error -> {
            logger.warning("Redis operation failed or timed out; using the in-memory fallback. "
                    + "Connection details are hidden.");
            return fallback.get();
        });
    }
}
