package com.bank.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "notification_logs",
    indexes = {
        @Index(name = "idx_notification_user_id", columnList = "user_id"),
        @Index(name = "idx_notification_status",  columnList = "status"),
        @Index(name = "idx_notification_sent_at",  columnList = "sent_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID ของผู้ใช้ที่ต้องการส่งแจ้งเตือน */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** เบอร์โทรศัพท์ผู้รับ (ถ้ามี) */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /** ข้อความแจ้งเตือนที่ส่งไป */
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** สถานะการส่ง: PENDING, SENT, FAILED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    /** ประเภทของการแจ้งเตือน */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 50)
    private NotificationType notificationType;

    /** Transaction Reference ID ที่เกี่ยวข้อง */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /** จำนวนเงินในธุรกรรม */
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    /** เวลาที่ส่งการแจ้งเตือน */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** เวลาที่สร้าง record */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** เหตุผลที่ล้มเหลว (ถ้ามี) */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** จำนวนครั้งที่ retry */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    // ─── Enums ────────────────────────────────────────────────────────────

    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED
    }

    public enum NotificationType {
        TRANSFER_SUCCESS,
        TRANSFER_FAILED,
        DEPOSIT_SUCCESS,
        WITHDRAWAL_SUCCESS,
        LOW_BALANCE_ALERT
    }
}
