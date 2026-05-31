package com.bank.notification.service;

import com.bank.notification.dto.SmsResult;
import com.bank.notification.dto.TransactionEvent;
import com.bank.notification.entity.NotificationLog;
import com.bank.notification.entity.NotificationLog.NotificationStatus;
import com.bank.notification.entity.NotificationLog.NotificationType;
import com.bank.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final SmsService smsService;

    // ─── Process Transfer Success ──────────────────────────────────────────

    /**
     * ประมวลผลเมื่อได้รับ Event โอนเงินสำเร็จ
     * - ส่ง SMS หา Sender
     * - ส่ง SMS หา Receiver
     * - บันทึก Log ลง DB
     */
    @Transactional
    public void processTransferSuccess(TransactionEvent event) {
        log.info("🔔 เริ่มประมวลผล Transfer Success Event: transactionId={}", event.getTransactionId());

        // ─── แจ้งเตือนผู้โอน (Sender) ────────────────────────────────────
        if (event.getSenderId() != null) {
            processSenderNotification(event);
        }

        // ─── แจ้งเตือนผู้รับ (Receiver) ──────────────────────────────────
        if (event.getReceiverId() != null) {
            processReceiverNotification(event);
        }

        log.info("✅ ประมวลผล Transfer Success Event เสร็จสิ้น: transactionId={}", event.getTransactionId());
    }

    /**
     * ประมวลผลเมื่อได้รับ Event โอนเงินล้มเหลว
     */
    @Transactional
    public void processTransferFailed(TransactionEvent event) {
        log.info("🔔 เริ่มประมวลผล Transfer Failed Event: transactionId={}", event.getTransactionId());

        if (event.getSenderId() == null) {
            log.warn("ไม่พบ senderId ใน Event - ข้ามการแจ้งเตือน");
            return;
        }

        Long userId = event.getSenderId();
        String phoneNumber = smsService.resolvePhoneNumber(userId);

        // สร้าง Notification Log เริ่มต้น (PENDING)
        NotificationLog notificationLog = buildNotificationLog(
            userId,
            phoneNumber,
            "",
            NotificationStatus.PENDING,
            NotificationType.TRANSFER_FAILED,
            event
        );
        notificationLog = notificationLogRepository.save(notificationLog);

        // ส่ง SMS
        SmsResult result = smsService.sendTransferFailed(
            phoneNumber,
            event.getAmount(),
            event.getFailureReason() != null ? event.getFailureReason() : "ไม่ทราบสาเหตุ",
            event.getTransactionId()
        );

        // อัปเดตสถานะ
        updateNotificationStatus(notificationLog, result);
        notificationLogRepository.save(notificationLog);

        log.info("✅ ประมวลผล Transfer Failed Event เสร็จสิ้น: transactionId={}", event.getTransactionId());
    }

    // ─── Private Helpers ───────────────────────────────────────────────────

    private void processSenderNotification(TransactionEvent event) {
        Long userId = event.getSenderId();
        String phoneNumber = smsService.resolvePhoneNumber(userId);

        log.debug("📨 ส่ง SMS แจ้งเตือนผู้โอน: userId={}, phone={}", userId,
            phoneNumber.substring(0, 3) + "****");

        // บันทึก PENDING ก่อน
        NotificationLog log1 = buildNotificationLog(
            userId, phoneNumber, "", NotificationStatus.PENDING,
            NotificationType.TRANSFER_SUCCESS, event
        );
        log1 = notificationLogRepository.save(log1);

        // ส่ง SMS จริง
        SmsResult result = smsService.sendTransferSuccessSender(
            userId,
            phoneNumber,
            event.getAmount(),
            event.getCurrency(),
            event.getReceiverId(),
            event.getSenderRemainingBalance(),
            event.getTransactionId(),
            event.getTransactionTime() != null ? event.getTransactionTime() : LocalDateTime.now()
        );

        // อัปเดต message และสถานะ
        log1.setMessage(result.getMessage() != null ? result.getMessage() : "");
        updateNotificationStatus(log1, result);
        notificationLogRepository.save(log1);

        log.info("   └─ Sender SMS {}: userId={}", result.isSuccess() ? "✅ SENT" : "❌ FAILED", userId);
    }

    private void processReceiverNotification(TransactionEvent event) {
        Long userId = event.getReceiverId();
        String phoneNumber = smsService.resolvePhoneNumber(userId);

        log.debug("📨 ส่ง SMS แจ้งเตือนผู้รับ: userId={}", userId);

        // บันทึก PENDING ก่อน
        NotificationLog log2 = buildNotificationLog(
            userId, phoneNumber, "", NotificationStatus.PENDING,
            NotificationType.TRANSFER_SUCCESS, event
        );
        log2 = notificationLogRepository.save(log2);

        // ส่ง SMS จริง
        SmsResult result = smsService.sendTransferSuccessReceiver(
            userId,
            phoneNumber,
            event.getAmount(),
            event.getCurrency(),
            event.getSenderId(),
            event.getTransactionId(),
            event.getTransactionTime() != null ? event.getTransactionTime() : LocalDateTime.now()
        );

        // อัปเดต message และสถานะ
        log2.setMessage(result.getMessage() != null ? result.getMessage() : "");
        updateNotificationStatus(log2, result);
        notificationLogRepository.save(log2);

        log.info("   └─ Receiver SMS {}: userId={}", result.isSuccess() ? "✅ SENT" : "❌ FAILED", userId);
    }

    private NotificationLog buildNotificationLog(
        Long userId,
        String phoneNumber,
        String message,
        NotificationStatus status,
        NotificationType type,
        TransactionEvent event
    ) {
        return NotificationLog.builder()
            .userId(userId)
            .phoneNumber(phoneNumber)
            .message(message)
            .status(status)
            .notificationType(type)
            .transactionId(event.getTransactionId())
            .amount(event.getAmount())
            .build();
    }

    private void updateNotificationStatus(NotificationLog notificationLog, SmsResult result) {
        if (result.isSuccess()) {
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(result.getSentAt());
        } else {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setFailureReason(result.getErrorMessage());
            notificationLog.setRetryCount(notificationLog.getRetryCount() + 1);
        }
    }

    // ─── Query Methods ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationLog> getNotificationsByUser(Long userId) {
        return notificationLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getNotificationsByStatus(NotificationStatus status) {
        return notificationLogRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getNotificationsByTransaction(String transactionId) {
        return notificationLogRepository.findByTransactionId(transactionId);
    }
}
