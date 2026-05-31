package com.bank.transaction.dto;

import com.bank.transaction.enums.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID txId;
    private Long userId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private TransactionStatus status;
    private String referenceNumber;
    private LocalDateTime occurredAt;
    private String eventType; // TRANSFER_COMPLETED, TRANSFER_FAILED, TRANSFER_COMPENSATED
}
