package com.uptimecrew.expense.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

// Behaviour:
//  1. Compute Redis key  idem:{namespace}:{key}.
//  2. If the value already exists, deserialise and return the cached response.
//  3. Else SETNX a sentinel with TTL 24h; if SETNX races, return 409 Conflict.
//  4. Run doWork.get(); serialise + store the result under the Redis key with
//     TTL 24h; return it.
@Service
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String IN_FLIGHT = "__in_flight__";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity handle(String key, String namespace, Supplier<ResponseEntity> doWork) {
        String redisKey = "idem:" + namespace + ":" + key;
        String existing = redis.opsForValue().get(redisKey);

        if (existing != null && !IN_FLIGHT.equals(existing)) {
            LOG.info("idempotency cache hit key={} namespace={}", key, namespace);
            try {
                Map body = mapper.readValue(existing, new TypeReference<>() {});
                return (ResponseEntity) ResponseEntity.ok(body);
            } catch (Exception ex) {
                LOG.warn("idempotency cached body unreadable; recomputing key={}", key);
            }
        }

        Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, IN_FLIGHT, TTL);
        if (Boolean.FALSE.equals(acquired)) {
            LOG.warn("idempotency conflict key={} namespace={}", key, namespace);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ResponseEntity result = doWork.get();
        try {
            String serialised = mapper.writeValueAsString(result.getBody());
            redis.opsForValue().set(redisKey, serialised, TTL);
        } catch (Exception ex) {
            LOG.warn("failed to cache idempotent body key={}; cache miss on retry", key, ex);
            redis.delete(redisKey);
        }
        return result;
    }
}
