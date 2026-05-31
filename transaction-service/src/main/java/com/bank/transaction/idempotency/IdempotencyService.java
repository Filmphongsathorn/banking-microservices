package com.bank.transaction.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency Service - ป้องกันการโอนเงินซ้ำ (Double Spending Prevention)
 *
 * Flow:
 * 1. รับ idempotencyKey จาก client
 * 2. เช็ค Redis ว่ามี key นี้แล้วหรือยัง
 * 3. ถ้ามีแล้ว = duplicate request → reject ทันที
 * 4. ถ้าไม่มี = ใหม่ → set key ใน Redis แล้ว proceed
 * 5. เก็บ txId ใน Redis เพื่อ return cached result ในกรณี retry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    // TTL 24 ชั่วโมง - ป้องกันซ้ำใน 24hr window
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String PROCESSING_PREFIX = "idempotency:processing:";

    /**
     * ตรวจสอบว่า key นี้เคยถูกใช้แล้วหรือยัง
     * @return Optional ที่มี txId ถ้าเคยทำแล้ว
     */
    public Optional<String> checkDuplicate(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String existingTxId = redisTemplate.opsForValue().get(redisKey);

        if (existingTxId != null) {
            log.warn("[Idempotency] Duplicate request detected for key={}, existing txId={}",
                maskKey(idempotencyKey), existingTxId);
            return Optional.of(existingTxId);
        }

        return Optional.empty();
    }

    /**
     * ล็อก idempotency key (atomic set-if-not-exists)
     * ใช้ SETNX เพื่อป้องกัน race condition
     * @return true ถ้า lock สำเร็จ (request ใหม่), false ถ้ามีแล้ว (duplicate)
     */
    public boolean acquireLock(String idempotencyKey) {
        String lockKey = PROCESSING_PREFIX + idempotencyKey;
        // SETNX = SET if Not eXists - atomic operation ป้องกัน race condition
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "PROCESSING", Duration.ofMinutes(5));
        
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("[Idempotency] Lock acquired for key={}", maskKey(idempotencyKey));
            return true;
        }
        
        log.warn("[Idempotency] Lock already exists (concurrent request) for key={}", maskKey(idempotencyKey));
        return false;
    }

    /**
     * บันทึก txId ลง Redis หลังทำธุรกรรมสำเร็จ
     */
    public void markCompleted(String idempotencyKey, UUID txId) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String lockKey = PROCESSING_PREFIX + idempotencyKey;

        redisTemplate.opsForValue().set(redisKey, txId.toString(), IDEMPOTENCY_TTL);
        redisTemplate.delete(lockKey); // ปล่อย processing lock

        log.info("[Idempotency] Marked completed for key={}, txId={}", maskKey(idempotencyKey), txId);
    }

    /**
     * ลบ lock กรณี transaction ล้มเหลว (อนุญาตให้ retry)
     */
    public void releaseLock(String idempotencyKey) {
        String lockKey = PROCESSING_PREFIX + idempotencyKey;
        redisTemplate.delete(lockKey);
        log.debug("[Idempotency] Lock released for key={}", maskKey(idempotencyKey));
    }

    /**
     * ตรวจสอบทั้ง duplicate และ concurrent request
     */
    public IdempotencyCheckResult check(String idempotencyKey) {
        // 1. ตรวจสอบว่าทำไปแล้ว
        Optional<String> existingTxId = checkDuplicate(idempotencyKey);
        if (existingTxId.isPresent()) {
            return IdempotencyCheckResult.duplicate(existingTxId.get());
        }

        // 2. พยายาม acquire lock
        boolean locked = acquireLock(idempotencyKey);
        if (!locked) {
            return IdempotencyCheckResult.concurrent();
        }

        return IdempotencyCheckResult.proceed();
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    // ====================================================
    // Result class
    // ====================================================
    public record IdempotencyCheckResult(
        ResultType type,
        String existingTxId
    ) {
        public enum ResultType { PROCEED, DUPLICATE, CONCURRENT }

        public static IdempotencyCheckResult proceed() {
            return new IdempotencyCheckResult(ResultType.PROCEED, null);
        }

        public static IdempotencyCheckResult duplicate(String txId) {
            return new IdempotencyCheckResult(ResultType.DUPLICATE, txId);
        }

        public static IdempotencyCheckResult concurrent() {
            return new IdempotencyCheckResult(ResultType.CONCURRENT, null);
        }

        public boolean isProceed() { return type == ResultType.PROCEED; }
        public boolean isDuplicate() { return type == ResultType.DUPLICATE; }
        public boolean isConcurrent() { return type == ResultType.CONCURRENT; }
    }
}
