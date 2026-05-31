package com.bank.notification.service;

import com.bank.notification.dto.SmsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SMS Service จำลองการส่ง SMS (Simulator)
 * ในระบบจริงให้เชื่อมต่อกับ Provider เช่น Twilio, AWS SNS, หรือ True Move API
 */
@Service
@Slf4j
public class SmsService {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final NumberFormat CURRENCY_FORMAT =
        NumberFormat.getNumberInstance(new Locale("th", "TH"));

    @Value("${notification.sms.sender-name:BANK-ALERT}")
    private String senderName;

    /**
     * จำลองการส่ง SMS แจ้งโอนเงินสำเร็จ (ฝั่งผู้โอน)
     */
    public SmsResult sendTransferSuccessSender(
        Long userId,
        String phoneNumber,
        BigDecimal amount,
        String currency,
        Long receiverId,
        BigDecimal remainingBalance,
        String transactionId,
        LocalDateTime transactionTime
    ) {
        String message = buildTransferSenderMessage(
            amount, currency, receiverId, remainingBalance, transactionTime, transactionId
        );
        return sendSms(phoneNumber, message);
    }

    /**
     * จำลองการส่ง SMS แจ้งรับเงินสำเร็จ (ฝั่งผู้รับ)
     */
    public SmsResult sendTransferSuccessReceiver(
        Long userId,
        String phoneNumber,
        BigDecimal amount,
        String currency,
        Long senderId,
        String transactionId,
        LocalDateTime transactionTime
    ) {
        String message = buildTransferReceiverMessage(
            amount, currency, senderId, transactionTime, transactionId
        );
        return sendSms(phoneNumber, message);
    }

    /**
     * จำลองการส่ง SMS แจ้งโอนเงินล้มเหลว
     */
    public SmsResult sendTransferFailed(
        String phoneNumber,
        BigDecimal amount,
        String reason,
        String transactionId
    ) {
        String message = String.format(
            "[%s] โอนเงินไม่สำเร็จ %.2f บาท เหตุผล: %s Ref:%s",
            senderName, amount, reason, transactionId.substring(0, 8).toUpperCase()
        );
        return sendSms(phoneNumber, message);
    }

    // ─── Core SMS Sender (Simulator) ──────────────────────────────────────

    /**
     * Core method จำลองการส่ง SMS
     * ในระบบจริง: เรียก HTTP API ของ SMS Provider
     */
    private SmsResult sendSms(String phoneNumber, String message) {
        log.info("📱 [SMS SIMULATOR] กำลังส่ง SMS...");
        log.info("   ├─ To      : {}", maskPhoneNumber(phoneNumber));
        log.info("   ├─ From    : {}", senderName);
        log.info("   └─ Message : {}", message);

        // จำลอง network delay (50-200ms)
        simulateDelay(50, 200);

        // จำลองความสำเร็จ 95% (5% fail เพื่อทดสอบ retry)
        boolean isSuccess = ThreadLocalRandom.current().nextDouble() < 0.95;

        if (isSuccess) {
            String messageId = "SMS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("   ✅ [SMS SIMULATOR] ส่งสำเร็จ! Message ID: {}", messageId);
            return SmsResult.success(messageId, phoneNumber, message);
        } else {
            log.warn("   ❌ [SMS SIMULATOR] ส่งไม่สำเร็จ (จำลอง network error)");
            return SmsResult.failure(phoneNumber, "NETWORK_ERROR", "Simulated network failure");
        }
    }

    // ─── Message Builders ──────────────────────────────────────────────────

    private String buildTransferSenderMessage(
        BigDecimal amount,
        String currency,
        Long receiverId,
        BigDecimal remainingBalance,
        LocalDateTime time,
        String transactionId
    ) {
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
        CURRENCY_FORMAT.setMinimumFractionDigits(2);

        return String.format(
            "[%s] โอนเงินสำเร็จ %s %s ไปยัง UserID:%d เมื่อ %s คงเหลือ %s %s Ref:%s",
            senderName,
            CURRENCY_FORMAT.format(amount),
            currency != null ? currency : "THB",
            receiverId,
            time.format(DATE_FORMATTER),
            CURRENCY_FORMAT.format(remainingBalance != null ? remainingBalance : BigDecimal.ZERO),
            currency != null ? currency : "THB",
            transactionId.substring(0, Math.min(8, transactionId.length())).toUpperCase()
        );
    }

    private String buildTransferReceiverMessage(
        BigDecimal amount,
        String currency,
        Long senderId,
        LocalDateTime time,
        String transactionId
    ) {
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
        CURRENCY_FORMAT.setMinimumFractionDigits(2);

        return String.format(
            "[%s] รับโอนเงิน %s %s จาก UserID:%d เมื่อ %s Ref:%s",
            senderName,
            CURRENCY_FORMAT.format(amount),
            currency != null ? currency : "THB",
            senderId,
            time.format(DATE_FORMATTER),
            transactionId.substring(0, Math.min(8, transactionId.length())).toUpperCase()
        );
    }

    // ─── Utilities ─────────────────────────────────────────────────────────

    /**
     * สร้างเบอร์โทรจาก userId (ในระบบจริงดึงจาก user-service)
     */
    public String resolvePhoneNumber(Long userId) {
        // จำลอง: ในระบบจริงเรียก user-service API
        String[] mockPhones = {"0812345678", "0823456789", "0834567890", "0845678901", "0856789012"};
        int index = (int) (userId % mockPhones.length);
        return mockPhones[index];
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    private void simulateDelay(int minMs, int maxMs) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(minMs, maxMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
