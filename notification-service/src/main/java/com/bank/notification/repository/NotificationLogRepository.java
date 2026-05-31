package com.bank.notification.repository;

import com.bank.notification.entity.NotificationLog;
import com.bank.notification.entity.NotificationLog.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /** ค้นหา Notification ทั้งหมดของ user */
    List<NotificationLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** ค้นหาตามสถานะ */
    List<NotificationLog> findByStatus(NotificationStatus status);

    /** ค้นหาตาม transactionId */
    List<NotificationLog> findByTransactionId(String transactionId);

    /** ค้นหา Notification ที่ล้มเหลวและยังสามารถ retry ได้ */
    @Query("""
        SELECT n FROM NotificationLog n
        WHERE n.status = 'FAILED'
          AND n.retryCount < :maxRetry
          AND n.createdAt >= :since
        ORDER BY n.createdAt ASC
        """)
    List<NotificationLog> findRetryableNotifications(
        @Param("maxRetry") int maxRetry,
        @Param("since") LocalDateTime since
    );

    /** สถิติการส่งตามสถานะ */
    @Query("""
        SELECT n.status, COUNT(n)
        FROM NotificationLog n
        GROUP BY n.status
        """)
    List<Object[]> countByStatus();

    /** จำนวน Notification ของ user ในช่วงเวลา */
    @Query("""
        SELECT COUNT(n) FROM NotificationLog n
        WHERE n.userId = :userId
          AND n.createdAt BETWEEN :from AND :to
        """)
    long countByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}
