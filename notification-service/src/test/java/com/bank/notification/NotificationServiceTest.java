package com.bank.notification;

import com.bank.notification.dto.TransactionEvent;
import com.bank.notification.entity.NotificationLog;
import com.bank.notification.repository.NotificationLogRepository;
import com.bank.notification.service.NotificationService;
import com.bank.notification.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private NotificationService notificationService;

    private TransactionEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = TransactionEvent.builder()
            .eventType("TRANSFER_SUCCESS")
            .transactionId("TXN-12345678")
            .senderId(1L)
            .receiverId(2L)
            .amount(new BigDecimal("5000.00"))
            .currency("THB")
            .status("SUCCESS")
            .transactionTime(LocalDateTime.now())
            .senderRemainingBalance(new BigDecimal("45000.00"))
            .description("Test transfer")
            .build();
    }

    @Test
    @DisplayName("processTransferSuccess ควรส่ง SMS ทั้ง Sender และ Receiver")
    void processTransferSuccess_shouldSendSmsToSenderAndReceiver() {
        // Arrange
        when(smsService.resolvePhoneNumber(any())).thenReturn("0812345678");
        when(notificationLogRepository.save(any())).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            log.setId(1L);
            return log;
        });
        when(smsService.sendTransferSuccessSender(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(com.bank.notification.dto.SmsResult.success("MSG-001", "0812345678", "test message"));
        when(smsService.sendTransferSuccessReceiver(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(com.bank.notification.dto.SmsResult.success("MSG-002", "0812345678", "test message"));

        // Act
        notificationService.processTransferSuccess(sampleEvent);

        // Assert: save ถูกเรียก 4 ครั้ง (PENDING + UPDATE สำหรับ sender และ receiver)
        verify(notificationLogRepository, times(4)).save(any(NotificationLog.class));
        verify(smsService, times(1)).sendTransferSuccessSender(any(), any(), any(), any(), any(), any(), any(), any());
        verify(smsService, times(1)).sendTransferSuccessReceiver(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processTransferSuccess ควรทำงานปกติแม้ Sender เป็น null")
    void processTransferSuccess_withNullSender_shouldOnlySendToReceiver() {
        // Arrange
        sampleEvent.setSenderId(null);
        when(smsService.resolvePhoneNumber(any())).thenReturn("0812345678");
        when(notificationLogRepository.save(any())).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            log.setId(2L);
            return log;
        });
        when(smsService.sendTransferSuccessReceiver(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(com.bank.notification.dto.SmsResult.success("MSG-003", "0812345678", "test"));

        // Act
        notificationService.processTransferSuccess(sampleEvent);

        // Assert: Sender ไม่ถูก call
        verify(smsService, never()).sendTransferSuccessSender(any(), any(), any(), any(), any(), any(), any(), any());
        verify(smsService, times(1)).sendTransferSuccessReceiver(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("isTransferSuccess helper method ควร return true สำหรับ event ที่ถูกต้อง")
    void transactionEvent_isTransferSuccess_shouldReturnTrue() {
        assertThat(sampleEvent.isTransferSuccess()).isTrue();
    }

    @Test
    @DisplayName("isTransferFailed helper method ควร return true สำหรับ event ล้มเหลว")
    void transactionEvent_isTransferFailed_shouldReturnTrue() {
        TransactionEvent failedEvent = TransactionEvent.builder()
            .eventType("TRANSFER_FAILED")
            .status("FAILED")
            .build();
        assertThat(failedEvent.isTransferFailed()).isTrue();
    }
}
