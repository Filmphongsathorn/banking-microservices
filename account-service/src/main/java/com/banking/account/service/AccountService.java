package com.banking.account.service;

import com.banking.account.dto.AccountDto;

import java.util.List;

/**
 * AccountService Interface — กำหนด Contract ของ Business Logic
 */
public interface AccountService {

    /**
     * สร้างบัญชีใหม่
     */
    AccountDto.AccountResponse createAccount(AccountDto.CreateRequest request);

    /**
     * ดึงข้อมูลบัญชีด้วย account_no
     */
    AccountDto.AccountResponse getAccount(String accountNo);

    /**
     * ดึงบัญชีทั้งหมดของ userId
     */
    List<AccountDto.AccountResponse> getAccountsByUserId(Long userId);

    /**
     * ฝากเงินเข้าบัญชี
     * — ดึง account ด้วย PESSIMISTIC_WRITE lock
     * — เพิ่ม balance
     * — บันทึก
     */
    AccountDto.TransactionResponse deposit(String accountNo, AccountDto.DepositRequest request);

    /**
     * ถอนเงินจากบัญชี
     * — ดึง account ด้วย PESSIMISTIC_WRITE lock
     * — เช็ค balance ว่าพอหรือไม่ → ถ้าไม่พอโยน InsufficientBalanceException
     * — ลด balance
     * — บันทึก
     */
    AccountDto.TransactionResponse withdraw(String accountNo, AccountDto.WithdrawRequest request);
}
