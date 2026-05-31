package com.banking.account.config;

import com.banking.account.dto.ApiResponse;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.AccountNotActiveException;
import com.banking.account.exception.DuplicateAccountException;
import com.banking.account.exception.InsufficientBalanceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — จัดการ Exception ทุกประเภทให้คืน JSON ที่สม่ำเสมอ
 * ป้องกัน stack trace หลุดออก API response
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 404 Not Found ────────────────────────────────────────────────────────

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "ACCOUNT_NOT_FOUND"));
    }

    // ─── 409 Conflict ─────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateAccount(DuplicateAccountException ex) {
        log.warn("Duplicate account: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "DUPLICATE_ACCOUNT"));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotActive(AccountNotActiveException ex) {
        log.warn("Account not active: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "ACCOUNT_NOT_ACTIVE"));
    }

    // ─── 422 Unprocessable Entity ─────────────────────────────────────────────

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<InsufficientBalanceDetail>> handleInsufficientBalance(
            InsufficientBalanceException ex) {

        log.warn("Insufficient balance: accountNo={}, balance={}, requested={}",
                ex.getAccountNo(), ex.getCurrentBalance(), ex.getRequestedAmount());

        // คืน detail เพิ่มเติมให้ caller ใช้แสดงผล
        InsufficientBalanceDetail detail = new InsufficientBalanceDetail(
                ex.getAccountNo(), ex.getCurrentBalance(), ex.getRequestedAmount()
        );

        ApiResponse<InsufficientBalanceDetail> response = ApiResponse.<InsufficientBalanceDetail>builder()
                .success(false)
                .error(new ApiResponse.ErrorDetail("INSUFFICIENT_BALANCE", ex.getMessage()))
                .data(detail)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    // ─── 400 Bad Request ──────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationError(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_ARGUMENT"));
    }

    // ─── 500 Internal Server Error ────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR"));
    }

    // ─── Inner Record ─────────────────────────────────────────────────────────

    public record InsufficientBalanceDetail(
            String accountNo,
            java.math.BigDecimal currentBalance,
            java.math.BigDecimal requestedAmount
    ) {}
}
