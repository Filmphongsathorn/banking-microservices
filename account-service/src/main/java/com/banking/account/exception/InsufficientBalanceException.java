package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * โยนเมื่อยอดเงินในบัญชีไม่เพียงพอสำหรับการถอน
 * HTTP 422 Unprocessable Entity — request ถูกต้องตาม format แต่ทำไม่ได้ทาง business logic
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientBalanceException extends RuntimeException {

    private final String accountNo;
    private final java.math.BigDecimal currentBalance;
    private final java.math.BigDecimal requestedAmount;

    public InsufficientBalanceException(String accountNo,
                                        java.math.BigDecimal currentBalance,
                                        java.math.BigDecimal requestedAmount) {
        super(String.format(
            "Insufficient balance on account [%s]: current=%.4f, requested=%.4f",
            accountNo, currentBalance, requestedAmount
        ));
        this.accountNo       = accountNo;
        this.currentBalance  = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public String getAccountNo()              { return accountNo; }
    public java.math.BigDecimal getCurrentBalance()  { return currentBalance; }
    public java.math.BigDecimal getRequestedAmount() { return requestedAmount; }
}
