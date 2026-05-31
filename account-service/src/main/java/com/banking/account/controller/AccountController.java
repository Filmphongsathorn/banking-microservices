package com.banking.account.controller;

import com.banking.account.dto.AccountDto;
import com.banking.account.dto.ApiResponse;
import com.banking.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AccountController — REST API สำหรับ Service อื่นเรียกใช้
 *
 * Base URL: /api/v1/accounts
 *
 * ─── Endpoints ───────────────────────────────────────────────────────────────
 *
 *  POST   /api/v1/accounts                        สร้างบัญชีใหม่
 *  GET    /api/v1/accounts/{accountNo}             ดูข้อมูลบัญชี
 *  GET    /api/v1/accounts/user/{userId}           ดูบัญชีทั้งหมดของ user
 *  POST   /api/v1/accounts/{accountNo}/deposit     ฝากเงิน
 *  POST   /api/v1/accounts/{accountNo}/withdraw    ถอนเงิน
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ─── POST /api/v1/accounts ────────────────────────────────────────────────

    /**
     * สร้างบัญชีใหม่
     * เรียกจาก: user-service หลัง register หรือ admin เปิดบัญชีให้ลูกค้า
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AccountDto.AccountResponse>> createAccount(
            @Valid @RequestBody AccountDto.CreateRequest request) {

        log.info("POST /api/v1/accounts - createAccount: accountNo={}", request.getAccountNo());
        AccountDto.AccountResponse response = accountService.createAccount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    // ─── GET /api/v1/accounts/{accountNo} ─────────────────────────────────────

    /**
     * ดูข้อมูลบัญชีด้วย account_no
     * เรียกจาก: transaction-service, transfer-service เพื่อเช็ค balance ก่อนทำรายการ
     */
    @GetMapping("/{accountNo}")
    public ResponseEntity<ApiResponse<AccountDto.AccountResponse>> getAccount(
            @PathVariable String accountNo) {

        log.info("GET /api/v1/accounts/{}", accountNo);
        AccountDto.AccountResponse response = accountService.getAccount(accountNo);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── GET /api/v1/accounts/user/{userId} ───────────────────────────────────

    /**
     * ดูบัญชีทั้งหมดของ userId
     * เรียกจาก: user-service, notification-service
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<AccountDto.AccountResponse>>> getAccountsByUser(
            @PathVariable Long userId) {

        log.info("GET /api/v1/accounts/user/{}", userId);
        List<AccountDto.AccountResponse> responses = accountService.getAccountsByUserId(userId);

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // ─── POST /api/v1/accounts/{accountNo}/deposit ────────────────────────────

    /**
     * ฝากเงินเข้าบัญชี
     *
     * เรียกจาก:
     *  - transfer-service (ฝั่ง destination account ของการโอนเงิน)
     *  - payment-service (รับเงิน)
     *
     * Request Body:
     *  {
     *    "amount": 1000.00,
     *    "reference": "TXN-20240101-001"  (optional)
     *  }
     */
    @PostMapping("/{accountNo}/deposit")
    public ResponseEntity<ApiResponse<AccountDto.TransactionResponse>> deposit(
            @PathVariable String accountNo,
            @Valid @RequestBody AccountDto.DepositRequest request) {

        log.info("POST /api/v1/accounts/{}/deposit - amount={}", accountNo, request.getAmount());
        AccountDto.TransactionResponse response = accountService.deposit(accountNo, request);

        return ResponseEntity.ok(ApiResponse.success("Deposit successful", response));
    }

    // ─── POST /api/v1/accounts/{accountNo}/withdraw ───────────────────────────

    /**
     * ถอนเงินจากบัญชี
     *
     * เรียกจาก:
     *  - transfer-service (ฝั่ง source account ของการโอนเงิน)
     *  - payment-service (จ่ายเงิน)
     *
     * ⚠️  ถ้ายอดเงินไม่พอ จะได้รับ HTTP 422 พร้อม error detail
     *
     * Request Body:
     *  {
     *    "amount": 500.00,
     *    "reference": "TXN-20240101-001"  (optional)
     *  }
     */
    @PostMapping("/{accountNo}/withdraw")
    public ResponseEntity<ApiResponse<AccountDto.TransactionResponse>> withdraw(
            @PathVariable String accountNo,
            @Valid @RequestBody AccountDto.WithdrawRequest request) {

        log.info("POST /api/v1/accounts/{}/withdraw - amount={}", accountNo, request.getAmount());
        AccountDto.TransactionResponse response = accountService.withdraw(accountNo, request);

        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful", response));
    }
}
