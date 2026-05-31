package com.bank.transaction.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotNull(message = "From account ID is required")
    private String fromAccountId;

    @NotNull(message = "To account ID is required")
    private String toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "10000000.00", message = "Amount exceeds maximum transfer limit")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 16, max = 255, message = "Idempotency key must be 16-255 characters")
    private String idempotencyKey;

    @NotNull(message = "User ID is required")
    private Long userId;

    @Size(max = 500, message = "Description too long")
    private String description;
}
