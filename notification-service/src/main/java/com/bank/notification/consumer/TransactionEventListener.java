package com.bank.notification.consumer;

import com.bank.notification.dto.TransactionEvent;
import com.bank.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer สำหรับ Topic 'transaction-events'
 * รับ Event จาก transaction-service แล้วส่งต่อให้ NotificationService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final NotificationService notificationService;

    private final ObjectMapper objectMapper = buildObjectMapper();

    // ─── Kafka Listener ────────────────────────────────────────────────────

    /**
     * Main Listener สำหรับ Topic 'transaction-events'
     *
     * - groupId: notification-service-group
     * - ack mode: MANUAL_IMMEDIATE (ack เองหลังประมวลผลสำเร็จ)
     */
    @KafkaListener(
        topics = "${notification.kafka.topics.transaction-events:transaction-events}",
        groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTransactionEvent(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📥 รับ Kafka Message");
        log.info("   ├─ Topic    : {}", topic);
        log.info("   ├─ Partition: {}", partition);
        log.info("   ├─ Offset   : {}", offset);
        log.info("   ├─ Key      : {}", record.key());
        log.info("   └─ Payload  : {}", record.value());

        try {
            // ─── Parse JSON → TransactionEvent ────────────────────────────
            TransactionEvent event = parseEvent(record.value());

            if (event == null) {
                log.error("❌ ไม่สามารถ parse Event ได้ - ACK และข้ามไป");
                acknowledgment.acknowledge();
                return;
            }

            log.info("📋 Event ที่ได้รับ:");
            log.info("   ├─ eventType    : {}", event.getEventType());
            log.info("   ├─ transactionId: {}", event.getTransactionId());
            log.info("   ├─ senderId     : {}", event.getSenderId());
            log.info("   ├─ receiverId   : {}", event.getReceiverId());
            log.info("   ├─ amount       : {} {}", event.getAmount(), event.getCurrency());
            log.info("   └─ status       : {}", event.getStatus());

            // ─── Route ตาม Event Type ──────────────────────────────────────
            routeEvent(event);

            // ─── ACK หลังประมวลผลสำเร็จ ───────────────────────────────────
            acknowledgment.acknowledge();
            log.info("✅ ACK สำเร็จ - Offset: {}", offset);

        } catch (Exception e) {
            log.error("❌ เกิดข้อผิดพลาดในการประมวลผล Kafka Message: {}", e.getMessage(), e);
            // NACK: ไม่ acknowledge → Kafka จะส่งซ้ำใน retry policy
            // หรือจะ acknowledge แล้วบันทึก Dead Letter Queue แทน
            acknowledgment.acknowledge(); // ป้องกัน infinite loop ใน dev
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ─── Event Router ──────────────────────────────────────────────────────

    private void routeEvent(TransactionEvent event) {
        if (event.getEventType() == null) {
            log.warn("⚠️  eventType เป็น null - ตรวจสอบ status แทน");
            routeByStatus(event);
            return;
        }

        switch (event.getEventType().toUpperCase()) {
            case "TRANSFER_SUCCESS" -> {
                log.info("🟢 Route → processTransferSuccess");
                notificationService.processTransferSuccess(event);
            }
            case "TRANSFER_FAILED" -> {
                log.info("🔴 Route → processTransferFailed");
                notificationService.processTransferFailed(event);
            }
            case "DEPOSIT_SUCCESS" -> {
                log.info("🔵 Route → DEPOSIT_SUCCESS (ยังไม่ implement)");
                // TODO: notificationService.processDepositSuccess(event);
            }
            case "WITHDRAWAL_SUCCESS" -> {
                log.info("🟡 Route → WITHDRAWAL_SUCCESS (ยังไม่ implement)");
                // TODO: notificationService.processWithdrawalSuccess(event);
            }
            default -> {
                log.warn("⚠️  ไม่รู้จัก eventType: {} - ข้ามการประมวลผล", event.getEventType());
            }
        }
    }

    private void routeByStatus(TransactionEvent event) {
        if ("SUCCESS".equalsIgnoreCase(event.getStatus())) {
            log.info("🟢 Route (by status) → processTransferSuccess");
            notificationService.processTransferSuccess(event);
        } else if ("FAILED".equalsIgnoreCase(event.getStatus())) {
            log.info("🔴 Route (by status) → processTransferFailed");
            notificationService.processTransferFailed(event);
        } else {
            log.warn("⚠️  ไม่สามารถ route ได้ - status: {}", event.getStatus());
        }
    }

    // ─── Parser ────────────────────────────────────────────────────────────

    private TransactionEvent parseEvent(String json) {
        if (json == null || json.isBlank()) {
            log.error("❌ Payload ว่างเปล่า");
            return null;
        }
        try {
            return objectMapper.readValue(json, TransactionEvent.class);
        } catch (Exception e) {
            log.error("❌ Parse JSON ล้มเหลว: {} | JSON: {}", e.getMessage(), json);
            return null;
        }
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }
}
