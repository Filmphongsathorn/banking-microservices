package com.banking.account.exception;

import com.banking.account.entity.Account;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * โยนเมื่อพยายามทำธุรกรรมกับบัญชีที่ไม่ได้อยู่ในสถานะ ACTIVE
 * HTTP 409 Conflict — state ของ resource ไม่รองรับ operation นี้
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String accountNo, Account.AccountStatus status) {
        super(String.format(
            "Account [%s] is not ACTIVE. Current status: %s", accountNo, status
        ));
    }
}
