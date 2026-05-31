package com.bank.transaction.service;

import com.bank.transaction.dto.*;
import com.bank.transaction.entity.Transaction;
import com.bank.transaction.enums.TransactionStatus;
import com.bank.transaction.exception.*;
import com.bank.transaction.feign.AccountServiceClient;
import com.bank.transaction.idempotency.IdempotencyService;
import com.bank.transaction.idempotency.IdempotencyService.IdempotencyCheckResult;
import com.bank.transaction.kafka.TransactionEventProducer;
import com.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TransactionService - สมองกลของระบบโอนเงิน
 *
 * ใช้ Saga Pattern (Choreography-based):
 * Step 1: Check Idempotency (Redis)
 * Step 2: Create PENDING transaction
 * Step 3: Debit from_account (Feign → account-service)
 * Step 4: Credit to_account (Feign → account-service)
 *   ↳ ถ้า Step 4 ล้มเหลว → Compensate: Credit back to from_account
 * Step 5: Mark COMPLETED + Publish Kafka event
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final TransactionMapper transactionMapper;
    private final StringRedisTemplate redisTemplate;

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000.00");
    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    // ====================================================
    // MAIN TRANSFER FLOW
    // ====================================================

    public TransactionResponse transfer(TransferRequest request) {
        log.info("[Saga] Starting transfer: from={}, to={}, amount={}, idempotencyKey={}",
                request.getFromAccountId(), request.getToAccountId(),
                request.getAmount(), maskKey(request.getIdempotencyKey()));

        // ============================================================
        // STEP 1: Idempotency Check (Redis)
        // ============================================================
        IdempotencyCheckResult idempotencyResult = idempotencyService.check(request.getIdempotencyKey());

        if (idempotencyResult.isDuplicate()) {
            log.warn("[Saga] Duplicate request detected, returning cached result");
            return getCachedTransactionResponse(idempotencyResult.existingTxId());
        }

        if (idempotencyResult.isConcurrent()) {
            throw new DuplicateTransactionException(
                "A concurrent request with the same idempotency key is being processed. Please retry after a moment."
            );
        }

        // ============================================================
        // STEP 2: Validate, Anti-Fraud & Create PENDING Transaction
        // ============================================================
        validateTransferRequest(request);
        checkVelocityLimit(request.getUserId());
        checkDailyTransferLimit(request.getUserId(), request.getAmount());

        Transaction transaction = createPendingTransaction(request);
        log.info("[Saga] Transaction created: txId={}", transaction.getTxId());

        try {
            // ============================================================
            // STEP 3: Debit from_account
            // ============================================================
            executeDebit(transaction);

            // ============================================================
            // STEP 4: Credit to_account (with Saga compensation on failure)
            // ============================================================
            executeCredit(transaction);

            // ============================================================
            // STEP 5: Mark COMPLETED + Publish Kafka Event
            // ============================================================
            TransactionResponse response = completeTransaction(transaction);

            // ลงทะเบียน idempotency key → txId ใน Redis
            idempotencyService.markCompleted(request.getIdempotencyKey(), transaction.getTxId());

            log.info("[Saga] Transfer COMPLETED: txId={}", transaction.getTxId());
            return response;

        } catch (Exception e) {
            // Release idempotency lock เพื่อให้ retry ได้
            idempotencyService.releaseLock(request.getIdempotencyKey());
            throw e;
        }
    }

    // ====================================================
    // DEPOSIT & WITHDRAW FLOWS
    // ====================================================

    public TransactionResponse deposit(DepositRequest request) {
        log.info("[Saga] Starting deposit: account={}, amount={}, idempotencyKey={}",
                request.getAccountId(), request.getAmount(), maskKey(request.getIdempotencyKey()));

        IdempotencyCheckResult idempotencyResult = idempotencyService.check(request.getIdempotencyKey());
        if (idempotencyResult.isDuplicate()) {
            return getCachedTransactionResponse(idempotencyResult.existingTxId());
        }
        if (idempotencyResult.isConcurrent()) {
            throw new DuplicateTransactionException("A concurrent request with the same idempotency key is being processed. Please retry after a moment.");
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId("SYSTEM_DEPOSIT")
                .toAccountId(request.getAccountId())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .userId(request.getUserId())
                .description(request.getDescription())
                .referenceNumber(generateReferenceNumber())
                .build();
        transaction = transactionRepository.save(transaction);
        checkVelocityLimit(request.getUserId());

        try {
            executeDepositCredit(transaction);
            TransactionResponse response = completeTransaction(transaction);
            idempotencyService.markCompleted(request.getIdempotencyKey(), transaction.getTxId());
            log.info("[Saga] Deposit COMPLETED: txId={}", transaction.getTxId());
            return response;
        } catch (Exception e) {
            idempotencyService.releaseLock(request.getIdempotencyKey());
            throw e;
        }
    }

    public TransactionResponse withdraw(WithdrawRequest request) {
        log.info("[Saga] Starting withdraw: account={}, amount={}, idempotencyKey={}",
                request.getAccountId(), request.getAmount(), maskKey(request.getIdempotencyKey()));

        IdempotencyCheckResult idempotencyResult = idempotencyService.check(request.getIdempotencyKey());
        if (idempotencyResult.isDuplicate()) {
            return getCachedTransactionResponse(idempotencyResult.existingTxId());
        }
        if (idempotencyResult.isConcurrent()) {
            throw new DuplicateTransactionException("A concurrent request with the same idempotency key is being processed. Please retry after a moment.");
        }

        Transaction transaction = Transaction.builder()
                .fromAccountId(request.getAccountId())
                .toAccountId("SYSTEM_WITHDRAW")
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .userId(request.getUserId())
                .description(request.getDescription())
                .referenceNumber(generateReferenceNumber())
                .build();
        transaction = transactionRepository.save(transaction);
        checkVelocityLimit(request.getUserId());

        try {
            executeWithdrawDebit(transaction);
            TransactionResponse response = completeTransaction(transaction);
            idempotencyService.markCompleted(request.getIdempotencyKey(), transaction.getTxId());
            log.info("[Saga] Withdraw COMPLETED: txId={}", transaction.getTxId());
            return response;
        } catch (Exception e) {
            idempotencyService.releaseLock(request.getIdempotencyKey());
            throw e;
        }
    }

    // ====================================================
    // SAGA STEPS
    // ====================================================

    @Transactional
    protected Transaction createPendingTransaction(TransferRequest request) {
        Transaction transaction = Transaction.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .userId(request.getUserId())
                .description(request.getDescription())
                .referenceNumber(generateReferenceNumber())
                .build();

        return transactionRepository.save(transaction);
    }

    @Transactional
    protected void executeDebit(Transaction transaction) {
        log.info("[Saga] Step 3 - Debit: accountId={}, amount={}",
                transaction.getFromAccountId(), transaction.getAmount());

        updateTransactionStatus(transaction, TransactionStatus.PROCESSING);

        try {
            AccountBalanceUpdateRequest debitRequest = AccountBalanceUpdateRequest.builder()
                    .accountId(transaction.getFromAccountId())
                    .amount(transaction.getAmount())
                    .transactionId(transaction.getTxId().toString())
                    .operation("DEBIT")
                    .description("Transfer to " + transaction.getToAccountId())
                    .build();

            ApiResponse<AccountBalanceResponse> response =
                    accountServiceClient.debitAccount(transaction.getFromAccountId(), debitRequest);

            if (!response.isSuccess()) {
                throw new AccountServiceException("Debit failed: " + response.getMessage());
            }

            log.info("[Saga] Step 3 DONE - Debit successful: accountId={}", transaction.getFromAccountId());

        } catch (InsufficientFundsException e) {
            log.warn("[Saga] Step 3 FAILED - Insufficient funds: accountId={}", transaction.getFromAccountId());
            markTransactionFailed(transaction, "Insufficient funds: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[Saga] Step 3 FAILED - Debit error: {}", e.getMessage(), e);
            markTransactionFailed(transaction, "Debit failed: " + e.getMessage());
            throw new AccountServiceException("Failed to debit account: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected void executeCredit(Transaction transaction) {
        log.info("[Saga] Step 4 - Credit: accountId={}, amount={}",
                transaction.getToAccountId(), transaction.getAmount());

        try {
            AccountBalanceUpdateRequest creditRequest = AccountBalanceUpdateRequest.builder()
                    .accountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .transactionId(transaction.getTxId().toString())
                    .operation("CREDIT")
                    .description("Transfer from " + transaction.getFromAccountId())
                    .build();

            ApiResponse<AccountBalanceResponse> response =
                    accountServiceClient.creditAccount(transaction.getToAccountId(), creditRequest);

            if (!response.isSuccess()) {
                throw new AccountServiceException("Credit failed: " + response.getMessage());
            }

            log.info("[Saga] Step 4 DONE - Credit successful: accountId={}", transaction.getToAccountId());

        } catch (Exception e) {
            // ============================================================
            // SAGA COMPENSATION: Credit to_account ล้มเหลว → คืนเงิน from_account
            // ============================================================
            log.error("[Saga] Step 4 FAILED - Credit error, initiating COMPENSATION: {}", e.getMessage(), e);
            executeCompensation(transaction, "Credit failed - compensating: " + e.getMessage());
            throw new SagaCompensationException(
                "Transfer failed and was rolled back. Refund has been issued.", e
            );
        }
    }

    @Transactional
    protected void executeDepositCredit(Transaction transaction) {
        updateTransactionStatus(transaction, TransactionStatus.PROCESSING);
        try {
            AccountBalanceUpdateRequest creditRequest = AccountBalanceUpdateRequest.builder()
                    .accountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .transactionId(transaction.getTxId().toString())
                    .operation("CREDIT")
                    .description(transaction.getDescription() != null ? transaction.getDescription() : "Deposit")
                    .build();
            ApiResponse<AccountBalanceResponse> response = accountServiceClient.creditAccount(transaction.getToAccountId(), creditRequest);
            if (!response.isSuccess()) {
                throw new AccountServiceException("Deposit failed: " + response.getMessage());
            }
        } catch (Exception e) {
            markTransactionFailed(transaction, "Deposit failed: " + e.getMessage());
            throw new AccountServiceException("Failed to deposit: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected void executeWithdrawDebit(Transaction transaction) {
        updateTransactionStatus(transaction, TransactionStatus.PROCESSING);
        try {
            AccountBalanceUpdateRequest debitRequest = AccountBalanceUpdateRequest.builder()
                    .accountId(transaction.getFromAccountId())
                    .amount(transaction.getAmount())
                    .transactionId(transaction.getTxId().toString())
                    .operation("DEBIT")
                    .description(transaction.getDescription() != null ? transaction.getDescription() : "Withdraw")
                    .build();
            ApiResponse<AccountBalanceResponse> response = accountServiceClient.debitAccount(transaction.getFromAccountId(), debitRequest);
            if (!response.isSuccess()) {
                throw new AccountServiceException("Withdraw failed: " + response.getMessage());
            }
        } catch (InsufficientFundsException e) {
            markTransactionFailed(transaction, "Insufficient funds: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            markTransactionFailed(transaction, "Withdraw failed: " + e.getMessage());
            throw new AccountServiceException("Failed to withdraw: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void executeCompensation(Transaction transaction, String reason) {
        log.warn("[Saga] COMPENSATING - Refunding amount={} to accountId={}",
                transaction.getAmount(), transaction.getFromAccountId());

        updateTransactionStatusWithReason(transaction, TransactionStatus.COMPENSATING, reason);

        try {
            AccountBalanceUpdateRequest compensateRequest = AccountBalanceUpdateRequest.builder()
                    .accountId(transaction.getFromAccountId())
                    .amount(transaction.getAmount())
                    .transactionId(transaction.getTxId().toString())
                    .operation("CREDIT")
                    .description("Compensation/Refund for failed transaction " + transaction.getTxId())
                    .build();

            ApiResponse<AccountBalanceResponse> response =
                    accountServiceClient.creditAccount(transaction.getFromAccountId(), compensateRequest);

            if (response.isSuccess()) {
                updateTransactionStatusWithReason(transaction, TransactionStatus.COMPENSATED,
                        "Successfully compensated: money returned to sender");
                log.info("[Saga] COMPENSATION SUCCESSFUL: txId={}", transaction.getTxId());

                // Publish compensation event to Kafka
                publishEvent(transaction, "TRANSFER_COMPENSATED");
            } else {
                // Compensation ล้มเหลวด้วย - ต้องแจ้งเตือน manual intervention
                log.error("[Saga] COMPENSATION FAILED: txId={} - REQUIRES MANUAL INTERVENTION",
                        transaction.getTxId());
                updateTransactionStatusWithReason(transaction, TransactionStatus.FAILED,
                        "CRITICAL: Compensation failed - manual intervention required. " + response.getMessage());
                // TODO: Alert Ops team / PagerDuty / Incident management
            }

        } catch (Exception compensationEx) {
            log.error("[Saga] COMPENSATION EXCEPTION: txId={} - REQUIRES MANUAL INTERVENTION",
                    transaction.getTxId(), compensationEx);
            updateTransactionStatusWithReason(transaction, TransactionStatus.FAILED,
                    "CRITICAL: Compensation exception - manual intervention required: " + compensationEx.getMessage());
            // TODO: Alert Ops team
        }
    }

    @Transactional
    protected TransactionResponse completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        // Publish COMPLETED event to Kafka topic 'transaction-events'
        publishEvent(saved, "TRANSFER_COMPLETED");

        return transactionMapper.toResponse(saved);
    }

    // ====================================================
    // QUERY METHODS
    // ====================================================

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID txId) {
        Transaction transaction = transactionRepository.findByTxId(txId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + txId));
        return transactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccount(String accountId, Pageable pageable) {
        return transactionRepository.findAllByAccountId(accountId, pageable)
                .map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByUser(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(transactionMapper::toResponse);
    }

    // ====================================================
    // HELPER METHODS
    // ====================================================

    private void validateTransferRequest(TransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new InvalidTransactionException("Cannot transfer to the same account");
        }
    }

    private void checkVelocityLimit(Long userId) {
        String key = "velocity:user:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            throw new InvalidTransactionException("Too many transactions. Please try again later.");
        }
    }

    private void checkDailyTransferLimit(Long userId, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        
        List<Transaction> todayTxs = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent().stream()
            .filter(tx -> !tx.getFromAccountId().startsWith("SYSTEM_"))
            .filter(tx -> tx.getCreatedAt().isAfter(startOfDay) && tx.getCreatedAt().isBefore(endOfDay))
            .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
            .toList();
            
        BigDecimal totalToday = todayTxs.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        if (totalToday.add(amount).compareTo(DAILY_LIMIT) > 0) {
            throw new InvalidTransactionException("Transfer exceeds daily limit of " + DAILY_LIMIT);
        }
    }

    private void updateTransactionStatus(Transaction transaction, TransactionStatus status) {
        transaction.setStatus(status);
        transactionRepository.save(transaction);
    }

    private void updateTransactionStatusWithReason(Transaction transaction,
                                                    TransactionStatus status,
                                                    String reason) {
        transaction.setStatus(status);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
    }

    private void markTransactionFailed(Transaction transaction, String reason) {
        updateTransactionStatusWithReason(transaction, TransactionStatus.FAILED, reason);
    }

    private TransactionResponse getCachedTransactionResponse(String txIdStr) {
        UUID txId = UUID.fromString(txIdStr);
        return transactionRepository.findByTxId(txId)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException("Cached transaction not found: " + txId));
    }

    private void publishEvent(Transaction transaction, String eventType) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .txId(transaction.getTxId())
                    .userId(transaction.getUserId())
                    .fromAccountId(transaction.getFromAccountId())
                    .toAccountId(transaction.getToAccountId())
                    .amount(transaction.getAmount())
                    .status(transaction.getStatus())
                    .referenceNumber(transaction.getReferenceNumber())
                    .eventType(eventType)
                    .occurredAt(LocalDateTime.now())
                    .build();

            eventProducer.publishTransactionEvent(event);
        } catch (Exception e) {
            // Kafka publish failure ไม่ควร rollback transaction ที่ทำสำเร็จแล้ว
            log.error("[Saga] Failed to publish Kafka event for txId={}: {}",
                    transaction.getTxId(), e.getMessage(), e);
        }
    }

    private String generateReferenceNumber() {
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
