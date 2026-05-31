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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService - Saga Pattern Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TransactionEventProducer eventProducer;
    @Mock private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    private TransferRequest validRequest;
    private Transaction savedTransaction;
    private UUID txId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        txId          = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId   = UUID.randomUUID();
        userId        = UUID.randomUUID();

        validRequest = TransferRequest.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("1000.00"))
                .idempotencyKey("unique-key-" + UUID.randomUUID())
                .userId(userId)
                .description("Test transfer")
                .build();

        savedTransaction = Transaction.builder()
                .txId(txId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("1000.00"))
                .status(TransactionStatus.PENDING)
                .idempotencyKey(validRequest.getIdempotencyKey())
                .userId(userId)
                .referenceNumber("TXN123")
                .build();
    }

    // ====================================================
    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should reject duplicate transaction immediately")
        void shouldRejectDuplicateTransaction() {
            // Given
            String existingTxId = txId.toString();
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.duplicate(existingTxId));
            when(transactionRepository.findByTxId(txId))
                .thenReturn(Optional.of(savedTransaction));
            when(transactionMapper.toResponse(savedTransaction))
                .thenReturn(buildTransactionResponse(TransactionStatus.COMPLETED));

            // When
            TransactionResponse result = transactionService.transfer(validRequest);

            // Then
            assertThat(result).isNotNull();
            verify(accountServiceClient, never()).debitAccount(any(), any());
            verify(accountServiceClient, never()).creditAccount(any(), any());
        }

        @Test
        @DisplayName("Should throw exception for concurrent duplicate request")
        void shouldThrowForConcurrentRequest() {
            // Given
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.concurrent());

            // When / Then
            assertThatThrownBy(() -> transactionService.transfer(validRequest))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("concurrent");
        }
    }

    // ====================================================
    @Nested
    @DisplayName("Happy Path - Successful Transfer")
    class SuccessfulTransferTests {

        @Test
        @DisplayName("Should complete transfer successfully (Full Saga)")
        void shouldCompleteTransferSuccessfully() {
            // Given
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.proceed());
            when(transactionRepository.save(any()))
                .thenReturn(savedTransaction);

            ApiResponse<AccountBalanceResponse> successResponse = buildSuccessAccountResponse();
            when(accountServiceClient.debitAccount(eq(fromAccountId), any()))
                .thenReturn(successResponse);
            when(accountServiceClient.creditAccount(eq(toAccountId), any()))
                .thenReturn(successResponse);

            TransactionResponse expectedResponse = buildTransactionResponse(TransactionStatus.COMPLETED);
            when(transactionMapper.toResponse(any())).thenReturn(expectedResponse);

            // When
            TransactionResponse result = transactionService.transfer(validRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            verify(accountServiceClient).debitAccount(eq(fromAccountId), any());
            verify(accountServiceClient).creditAccount(eq(toAccountId), any());
            verify(idempotencyService).markCompleted(eq(validRequest.getIdempotencyKey()), eq(txId));
            verify(eventProducer).publishTransactionEvent(any());
        }
    }

    // ====================================================
    @Nested
    @DisplayName("Saga Compensation Tests")
    class SagaCompensationTests {

        @Test
        @DisplayName("Should compensate (refund) when credit fails")
        void shouldCompensateWhenCreditFails() {
            // Given
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.proceed());
            when(transactionRepository.save(any()))
                .thenReturn(savedTransaction);

            // Debit succeeds
            when(accountServiceClient.debitAccount(eq(fromAccountId), any()))
                .thenReturn(buildSuccessAccountResponse());

            // Credit fails
            when(accountServiceClient.creditAccount(eq(toAccountId), any()))
                .thenThrow(new AccountServiceException("Account not found"));

            // Compensation (credit back to from_account) succeeds
            when(accountServiceClient.creditAccount(eq(fromAccountId), any()))
                .thenReturn(buildSuccessAccountResponse());

            // When / Then
            assertThatThrownBy(() -> transactionService.transfer(validRequest))
                .isInstanceOf(SagaCompensationException.class);

            // Verify compensation was executed
            verify(accountServiceClient).debitAccount(eq(fromAccountId), any());
            verify(accountServiceClient).creditAccount(eq(toAccountId), any());
            // Compensation: credit back to fromAccount
            verify(accountServiceClient, atLeastOnce()).creditAccount(eq(fromAccountId), any());
        }

        @Test
        @DisplayName("Should fail cleanly when debit fails (no compensation needed)")
        void shouldFailWhenDebitFails() {
            // Given
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.proceed());
            when(transactionRepository.save(any()))
                .thenReturn(savedTransaction);

            when(accountServiceClient.debitAccount(eq(fromAccountId), any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));

            // When / Then
            assertThatThrownBy(() -> transactionService.transfer(validRequest))
                .isInstanceOf(InsufficientFundsException.class);

            // Verify credit was NEVER called (no debit = no need to compensate)
            verify(accountServiceClient, never()).creditAccount(eq(toAccountId), any());
        }
    }

    // ====================================================
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject transfer to same account")
        void shouldRejectSameAccountTransfer() {
            // Given
            validRequest.setToAccountId(fromAccountId); // same as from
            when(idempotencyService.check(anyString()))
                .thenReturn(IdempotencyCheckResult.proceed());
            when(transactionRepository.save(any()))
                .thenReturn(savedTransaction);

            // When / Then
            assertThatThrownBy(() -> transactionService.transfer(validRequest))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("same account");
        }
    }

    // ====================================================
    // Helpers
    // ====================================================
    private ApiResponse<AccountBalanceResponse> buildSuccessAccountResponse() {
        AccountBalanceResponse balanceResponse = AccountBalanceResponse.builder()
                .accountId(fromAccountId)
                .previousBalance(new BigDecimal("5000.00"))
                .currentBalance(new BigDecimal("4000.00"))
                .success(true)
                .build();
        return ApiResponse.<AccountBalanceResponse>builder()
                .success(true)
                .data(balanceResponse)
                .build();
    }

    private TransactionResponse buildTransactionResponse(TransactionStatus status) {
        return TransactionResponse.builder()
                .txId(txId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(new BigDecimal("1000.00"))
                .status(status)
                .build();
    }
}
