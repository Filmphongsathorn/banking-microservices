package com.bank.transaction.repository;

import com.bank.transaction.entity.Transaction;
import com.bank.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByTxId(UUID txId);

    Page<Transaction> findByFromAccountIdOrderByCreatedAtDesc(String fromAccountId, Pageable pageable);

    Page<Transaction> findByToAccountIdOrderByCreatedAtDesc(String toAccountId, Pageable pageable);

    Page<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
        ORDER BY t.createdAt DESC
    """)
    Page<Transaction> findAllByAccountId(@Param("accountId") String accountId, Pageable pageable);

    @Modifying
    @Query("UPDATE Transaction t SET t.status = :status WHERE t.txId = :txId")
    int updateStatus(@Param("txId") UUID txId, @Param("status") TransactionStatus status);

    @Modifying
    @Query("""
        UPDATE Transaction t 
        SET t.status = :status, t.failureReason = :reason, t.updatedAt = :updatedAt
        WHERE t.txId = :txId
    """)
    int updateStatusWithReason(
        @Param("txId") UUID txId,
        @Param("status") TransactionStatus status,
        @Param("reason") String reason,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime before);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
