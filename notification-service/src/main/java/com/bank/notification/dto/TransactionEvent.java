package com.bank.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO สำหรับรับ Event จาก Kafka Topic 'transaction-events'
 * ต้องตรงกับโครงสร้างที่ transaction-service ส่งมา
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {

    /** ประเภทของ Event */
    @JsonProperty("eventType")
    private String eventType;

    /** Transaction ID อ้างอิง */
    @JsonProperty("transactionId")
    private String transactionId;

    /** ID ของผู้โอน (Sender) */
    @JsonProperty("senderId")
    private Long senderId;

    /** ID ของผู้รับ (Receiver) */
    @JsonProperty("receiverId")
    private Long receiverId;

    /** จำนวนเงิน */
    @JsonProperty("amount")
    private BigDecimal amount;

    /** สกุลเงิน */
    @JsonProperty("currency")
    private String currency;

    /** สถานะธุรกรรม: SUCCESS, FAILED, PENDING */
    @JsonProperty("status")
    private String status;

    /** เวลาที่ทำธุรกรรม */
    @JsonProperty("transactionTime")
    private LocalDateTime transactionTime;

    /** ยอดคงเหลือของผู้โอนหลังทำธุรกรรม */
    @JsonProperty("senderRemainingBalance")
    private BigDecimal senderRemainingBalance;

    /** คำอธิบายเพิ่มเติม */
    @JsonProperty("description")
    private String description;

    /** เหตุผลที่ล้มเหลว (กรณี FAILED) */
    @JsonProperty("failureReason")
    private String failureReason;

    // ─── Helper Methods ────────────────────────────────────────────────────

    public boolean isTransferSuccess() {
        return "TRANSFER_SUCCESS".equalsIgnoreCase(eventType)
            && "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isTransferFailed() {
        return "TRANSFER_FAILED".equalsIgnoreCase(eventType)
            || "FAILED".equalsIgnoreCase(status);
    }
}
