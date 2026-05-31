package com.bank.transaction.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {
    private UUID accountId;
    private BigDecimal previousBalance;
    private BigDecimal currentBalance;
    private boolean success;
    private String message;
}
