package com.bank.transaction.kafka;

import com.bank.transaction.dto.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.transaction-events:transaction-events}")
    private String transactionEventsTopic;

    /**
     * Publish ธุรกรรมสำเร็จไปยัง Kafka topic 'transaction-events'
     * ใช้ txId เป็น key เพื่อให้ message เดียวกันไปอยู่ partition เดียวกัน (ordering)
     */
    public void publishTransactionEvent(TransactionEvent event) {
        String messageKey = event.getTxId().toString();

        log.info("[KafkaProducer] Publishing event: txId={}, status={}, topic={}",
                event.getTxId(), event.getStatus(), transactionEventsTopic);

        CompletableFuture<SendResult<String, TransactionEvent>> future =
                kafkaTemplate.send(transactionEventsTopic, messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KafkaProducer] Failed to publish event: txId={}, error={}",
                        event.getTxId(), ex.getMessage(), ex);
                // TODO: ส่งไป Dead Letter Queue หรือ retry mechanism
            } else {
                log.info("[KafkaProducer] Event published successfully: txId={}, partition={}, offset={}",
                        event.getTxId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish แบบ synchronous สำหรับกรณีที่ต้องการ guarantee ก่อน return
     */
    public void publishTransactionEventSync(TransactionEvent event) {
        String messageKey = event.getTxId().toString();

        try {
            SendResult<String, TransactionEvent> result =
                    kafkaTemplate.send(transactionEventsTopic, messageKey, event).get();

            log.info("[KafkaProducer] Event published sync: txId={}, partition={}, offset={}",
                    event.getTxId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("[KafkaProducer] Failed to publish event sync: txId={}", event.getTxId(), e);
            throw new RuntimeException("Failed to publish transaction event to Kafka", e);
        }
    }
}
