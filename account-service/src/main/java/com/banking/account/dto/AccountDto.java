package com.banking.account.dto;

import com.banking.account.entity.Account;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Objects สำหรับ Account Service
 * แยก DTO ออกจาก Entity เพื่อความปลอดภัยและ flexibility ในการ API versioning
 */
public class AccountDto {

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    /**
     * Request สำหรับสร้างบัญชีใหม่
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "account_no must not be blank")
        @Size(min = 10, max = 20, message = "account_no must be between 10-20 characters")
        @Pattern(regexp = "^[0-9]+$", message = "account_no must contain digits only")
        private String accountNo;

        @NotNull(message = "user_id must not be null")
        @Positive(message = "user_id must be positive")
        private Long userId;

        @NotNull(message = "initial balance must not be null")
        @DecimalMin(value = "0.0000", inclusive = true, message = "initial balance must be >= 0")
        @Digits(integer = 15, fraction = 4, message = "balance can have up to 15 integer digits and 4 decimal digits")
        private BigDecimal initialBalance;
    }

    /**
     * Request สำหรับฝากเงิน
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DepositRequest {

        @NotNull(message = "amount must not be null")
        @DecimalMin(value = "0.0001", message = "deposit amount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "amount can have up to 15 integer digits and 4 decimal digits")
        private BigDecimal amount;

        /** อ้างอิง transaction จาก service อื่น เช่น transfer-service */
        @Size(max = 100, message = "reference must not exceed 100 characters")
        private String reference;
    }

    /**
     * Request สำหรับถอนเงิน
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WithdrawRequest {

        @NotNull(message = "amount must not be null")
        @DecimalMin(value = "0.0001", message = "withdraw amount must be greater than 0")
        @Digits(integer = 15, fraction = 4, message = "amount can have up to 15 integer digits and 4 decimal digits")
        private BigDecimal amount;

        /** อ้างอิง transaction จาก service อื่น */
        @Size(max = 100, message = "reference must not exceed 100 characters")
        private String reference;
    }

    // ─── Response DTOs ────────────────────────────────────────────────────────

    /**
     * Response สำหรับข้อมูลบัญชี
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccountResponse {
        private Long id;
        private String accountNo;
        private Long userId;
        private BigDecimal balance;
        private Account.AccountStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** แปลง Entity → Response DTO */
        public static AccountResponse from(Account account) {
            return AccountResponse.builder()
                    .id(account.getId())
                    .accountNo(account.getAccountNo())
                    .userId(account.getUserId())
                    .balance(account.getBalance())
                    .status(account.getStatus())
                    .createdAt(account.getCreatedAt())
                    .updatedAt(account.getUpdatedAt())
                    .build();
        }
    }

    /**
     * Response หลังทำธุรกรรม deposit / withdraw
     * คืนยอดก่อนและหลังทำรายการ เพื่อให้ caller ตรวจสอบได้
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionResponse {
        private String accountNo;
        private String transactionType;   // DEPOSIT | WITHDRAW
        private BigDecimal amount;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private String reference;
        private LocalDateTime transactedAt;
    }
}
