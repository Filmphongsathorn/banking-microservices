package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * โยนเมื่อหาบัญชีไม่พบในระบบ
 * HTTP 404 Not Found
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountNo) {
        super(String.format("Account not found: [%s]", accountNo));
    }

    public AccountNotFoundException(Long id) {
        super(String.format("Account not found with id: [%d]", id));
    }
}
