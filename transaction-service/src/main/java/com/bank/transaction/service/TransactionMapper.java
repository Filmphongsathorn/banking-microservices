package com.bank.transaction.service;

import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) return null;
        return TransactionResponse.builder()
                .txId(transaction.getTxId())
                .fromAccountId(transaction.getFromAccountId())
                .toAccountId(transaction.getToAccountId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .referenceNumber(transaction.getReferenceNumber())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .userId(transaction.getUserId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}
