package com.bank.transaction.job;

import com.bank.transaction.entity.Transaction;
import com.bank.transaction.enums.TransactionStatus;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SagaRecoveryJob - 1000-Year Banker Standard Sweeper Job
 * ทำหน้าที่ค้นหา transaction ที่ค้างอยู่ในสถานะ PROCESSING หรือ COMPENSATING เกิน 5 นาที
 * เพื่อทำการ recovery (retry/compensate) ป้องกันเงินหายจากการตายของ service กลางคัน
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoveryJob {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    // Run every 1 minute
    @Scheduled(fixedRate = 60000)
    public void recoverStuckTransactions() {
        LocalDateTime fiveMinsAgo = LocalDateTime.now().minusMinutes(5);
        
        // Find stuck processing
        List<Transaction> stuckProcessing = transactionRepository.findByStatusAndCreatedAtBefore(
                TransactionStatus.PROCESSING, fiveMinsAgo);

        for (Transaction tx : stuckProcessing) {
            log.warn("[SagaRecovery] Found stuck PROCESSING transaction: txId={}. Initiating compensation...", tx.getTxId());
            try {
                // In a perfect Saga, we would query the target service to see if it actually succeeded.
                // For safety in this simplified model, if it's stuck, we refund to ensure no lost money.
                transactionService.executeCompensation(tx, "Auto-recovery: refunding stuck transaction");
            } catch (Exception e) {
                log.error("[SagaRecovery] Auto-compensation failed for txId={}: {}", tx.getTxId(), e.getMessage());
            }
        }
        
        // Find stuck compensating
        List<Transaction> stuckCompensating = transactionRepository.findByStatusAndCreatedAtBefore(
                TransactionStatus.COMPENSATING, fiveMinsAgo);

        for (Transaction tx : stuckCompensating) {
            log.warn("[SagaRecovery] Found stuck COMPENSATING transaction: txId={}. Retrying compensation...", tx.getTxId());
            try {
                transactionService.executeCompensation(tx, "Auto-recovery: retry compensation");
            } catch (Exception e) {
                log.error("[SagaRecovery] Auto-compensation retry failed for txId={}: {}", tx.getTxId(), e.getMessage());
            }
        }
    }
}
