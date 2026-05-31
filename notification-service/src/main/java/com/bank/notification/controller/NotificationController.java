package com.bank.notification.controller;

import com.bank.notification.dto.TransactionEvent;
import com.bank.notification.entity.NotificationLog;
import com.bank.notification.entity.NotificationLog.NotificationStatus;
import com.bank.notification.repository.NotificationLogRepository;
import com.bank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;

    /**
     * GET /api/v1/notifications/user/{userId}
     * ดูประวัติการแจ้งเตือนของ user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationLog>> getByUser(@PathVariable Long userId) {
        log.info("GET /api/v1/notifications/user/{}", userId);
        return ResponseEntity.ok(notificationService.getNotificationsByUser(userId));
    }

    /**
     * GET /api/v1/notifications/transaction/{transactionId}
     * ดูการแจ้งเตือนตาม transactionId
     */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<List<NotificationLog>> getByTransaction(
        @PathVariable String transactionId
    ) {
        return ResponseEntity.ok(notificationService.getNotificationsByTransaction(transactionId));
    }

    /**
     * GET /api/v1/notifications/status/{status}
     * ดูการแจ้งเตือนตามสถานะ
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<NotificationLog>> getByStatus(@PathVariable String status) {
        NotificationStatus notificationStatus = NotificationStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(notificationService.getNotificationsByStatus(notificationStatus));
    }

    /**
     * POST /api/v1/notifications/test/transfer-success
     * ทดสอบส่ง Event โอนเงินสำเร็จโดยตรง (ไม่ผ่าน Kafka)
     */
    @PostMapping("/test/transfer-success")
    public ResponseEntity<Map<String, Object>> testTransferSuccess(
        @RequestBody TransactionEvent event
    ) {
        log.info("🧪 Test Transfer Success: {}", event);
        notificationService.processTransferSuccess(event);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "processed");
        response.put("transactionId", event.getTransactionId());
        response.put("message", "Event processed - check notification logs");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/notifications/stats
     * สถิติการส่ง Notification
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Object[]> stats = notificationLogRepository.countByStatus();
        Map<String, Object> result = new HashMap<>();
        long total = 0;
        for (Object[] row : stats) {
            String statusName = row[0].toString();
            Long count = (Long) row[1];
            result.put(statusName, count);
            total += count;
        }
        result.put("TOTAL", total);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/notifications/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "service", "notification-service",
            "status", "UP",
            "version", "1.0.0"
        ));
    }
}
