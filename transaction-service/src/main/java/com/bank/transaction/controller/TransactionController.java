package com.bank.transaction.controller;

import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * POST /api/v1/transactions/transfer
     * โอนเงินระหว่างบัญชี
     */
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        log.info("[Controller] Transfer request received from={} to={} amount={}",
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        TransactionResponse response = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Transfer completed successfully"));
    }

    /**
     * POST /api/v1/transactions/deposit
     * ฝากเงิน
     */
    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody com.bank.transaction.dto.DepositRequest request
    ) {
        log.info("[Controller] Deposit request received account={} amount={}",
                request.getAccountId(), request.getAmount());

        TransactionResponse response = transactionService.deposit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Deposit completed successfully"));
    }

    /**
     * POST /api/v1/transactions/withdraw
     * ถอนเงิน
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody com.bank.transaction.dto.WithdrawRequest request
    ) {
        log.info("[Controller] Withdraw request received account={} amount={}",
                request.getAccountId(), request.getAmount());

        TransactionResponse response = transactionService.withdraw(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Withdraw completed successfully"));
    }

    /**
     * GET /api/v1/transactions/{txId}
     * ดูรายละเอียดธุรกรรม
     */
    @GetMapping("/{txId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID txId
    ) {
        TransactionResponse response = transactionService.getTransaction(txId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/transactions/account/{accountId}
     * ดูประวัติธุรกรรมของบัญชี
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getByAccount(
            @PathVariable String accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<TransactionResponse> page = transactionService.getTransactionsByAccount(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    /**
     * GET /api/v1/transactions/user/{userId}
     * ดูประวัติธุรกรรมของ user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<TransactionResponse> page = transactionService.getTransactionsByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
