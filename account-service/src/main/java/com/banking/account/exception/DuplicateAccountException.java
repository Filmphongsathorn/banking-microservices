package com.banking.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * โยนเมื่อพยายามสร้างบัญชีที่มี account_no ซ้ำกับที่มีอยู่แล้ว
 * HTTP 409 Conflict
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountNo) {
        super(String.format("Account already exists with account_no: [%s]", accountNo));
    }
}
