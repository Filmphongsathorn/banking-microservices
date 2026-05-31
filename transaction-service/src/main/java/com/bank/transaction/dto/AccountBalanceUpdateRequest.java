package com.bank.transaction.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceUpdateRequest {
    private String accountId;
    private BigDecimal amount;
    private String transactionId;
    private String operation; // DEBIT or CREDIT
    private String description;
}
