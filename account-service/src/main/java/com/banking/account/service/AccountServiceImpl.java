package com.banking.account.service;

import com.banking.account.entity.LedgerEntry;
import com.banking.account.repository.LedgerEntryRepository;

import com.banking.account.dto.AccountDto;
import com.banking.account.entity.Account;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.AccountNotActiveException;
import com.banking.account.exception.DuplicateAccountException;
import com.banking.account.exception.InsufficientBalanceException;
import com.banking.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AccountServiceImpl — Implementation หลักของ Business Logic
 *
 * ─── Transaction Strategy ────────────────────────────────────────────────────
 *
 * deposit() และ withdraw() ใช้:
 *   @Transactional(isolation = Isolation.READ_COMMITTED)
 *
 *   เหตุผล:
 *   1. PESSIMISTIC_WRITE lock ใน Repository จะ lock row ระดับ DB อยู่แล้ว
 *      (SELECT ... FOR UPDATE) ดังนั้น READ_COMMITTED เพียงพอ
 *
 *   2. SERIALIZABLE จะ overhead มากเกินไปและอาจเกิด deadlock บ่อยขึ้น
 *      ในระบบที่มี concurrent transactions สูง
 *
 *   3. Lock จะถูก release ตอน Transaction commit/rollback เท่านั้น
 *      ดังนั้น Transaction ต้องสั้นที่สุด — ไม่ควรเรียก external service
 *      ภายใน method ที่ถือ lock อยู่
 *
 * ─── Flow การ deposit / withdraw ────────────────────────────────────────────
 *
 *   Thread A                         Thread B (same account)
 *   ─────────────────────────────    ────────────────────────────────
 *   BEGIN TRANSACTION
 *   SELECT ... FOR UPDATE            BEGIN TRANSACTION
 *   (lock acquired)                  SELECT ... FOR UPDATE
 *                                    (⛔ blocked — รอ Thread A)
 *   UPDATE balance
 *   COMMIT (lock released)
 *                                    (✅ lock acquired)
 *                                    UPDATE balance
 *                                    COMMIT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // ─── Create ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AccountDto.AccountResponse createAccount(AccountDto.CreateRequest request) {
        log.info("Creating account: accountNo={}, userId={}", request.getAccountNo(), request.getUserId());

        // ตรวจสอบว่า account_no ไม่ซ้ำ
        if (accountRepository.existsByAccountNo(request.getAccountNo())) {
            throw new DuplicateAccountException(request.getAccountNo());
        }

        Account account = Account.builder()
                .accountNo(request.getAccountNo())
                .userId(request.getUserId())
                .balance(request.getInitialBalance())
                .status(Account.AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: id={}, accountNo={}", saved.getId(), saved.getAccountNo());

        return AccountDto.AccountResponse.from(saved);
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AccountDto.AccountResponse getAccount(String accountNo) {
        Account account = findAccountByNo(accountNo);
        return AccountDto.AccountResponse.from(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto.AccountResponse> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(AccountDto.AccountResponse::from)
                .collect(Collectors.toList());
    }

    // ─── Deposit ──────────────────────────────────────────────────────────────

    /**
     * ฝากเงินเข้าบัญชี
     *
     * ขั้นตอน:
     *  1. ดึงบัญชีพร้อม PESSIMISTIC_WRITE lock (SELECT FOR UPDATE)
     *  2. ตรวจสอบสถานะบัญชีต้อง ACTIVE
     *  3. validate จำนวนเงินต้องมากกว่า 0
     *  4. บวก balance
     *  5. save และ return TransactionResponse
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountDto.TransactionResponse deposit(String accountNo, AccountDto.DepositRequest request) {
        log.info("Deposit: accountNo={}, amount={}, reference={}",
                accountNo, request.getAmount(), request.getReference());

        // 1. Lock row ด้วย PESSIMISTIC_WRITE
        Account account = findAccountForUpdate(accountNo);

        // 2. ตรวจสอบสถานะ
        validateAccountIsActive(account);

        // 3. validate จำนวนเงิน
        validatePositiveAmount(request.getAmount(), "deposit");

        // 4. บันทึก balance ก่อนทำรายการ
        BigDecimal balanceBefore = account.getBalance();

        // 5. บวกเงิน
        account.setBalance(balanceBefore.add(request.getAmount()));

        // 6. save
        accountRepository.save(account);

        // 7. บันทึก Ledger Entry (Immutable Audit Trail)
        LedgerEntry ledgerEntry = LedgerEntry.builder()
                .accountId(accountNo)
                .transactionId(request.getReference() != null ? request.getReference() : "DEP-" + System.currentTimeMillis())
                .operation(LedgerEntry.Operation.CREDIT)
                .amount(request.getAmount())
                .runningBalance(account.getBalance())
                .description("Deposit")
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        log.info("Deposit success: accountNo={}, before={}, after={}, ref={}",
                accountNo, balanceBefore, account.getBalance(), request.getReference());

        return AccountDto.TransactionResponse.builder()
                .accountNo(accountNo)
                .transactionType("DEPOSIT")
                .amount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getBalance())
                .reference(request.getReference())
                .transactedAt(LocalDateTime.now())
                .build();
    }

    // ─── Withdraw ─────────────────────────────────────────────────────────────

    /**
     * ถอนเงินจากบัญชี
     *
     * ขั้นตอน:
     *  1. ดึงบัญชีพร้อม PESSIMISTIC_WRITE lock (SELECT FOR UPDATE)
     *  2. ตรวจสอบสถานะบัญชีต้อง ACTIVE
     *  3. validate จำนวนเงินต้องมากกว่า 0
     *  4. ตรวจสอบยอดเงินพอหรือไม่ → ถ้าไม่พอโยน InsufficientBalanceException
     *  5. ลด balance
     *  6. save และ return TransactionResponse
     *
     * ⚠️  balance ห้ามติดลบเด็ดขาด — enforce ทั้ง application layer (ที่นี่)
     *     และ DB constraint (CHECK balance >= 0 ในไฟล์ schema)
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountDto.TransactionResponse withdraw(String accountNo, AccountDto.WithdrawRequest request) {
        log.info("Withdraw: accountNo={}, amount={}, reference={}",
                accountNo, request.getAmount(), request.getReference());

        // 1. Lock row ด้วย PESSIMISTIC_WRITE
        Account account = findAccountForUpdate(accountNo);

        // 2. ตรวจสอบสถานะ
        validateAccountIsActive(account);

        // 3. validate จำนวนเงิน
        validatePositiveAmount(request.getAmount(), "withdraw");

        // 4. เช็คว่ายอดเงินพอหรือไม่
        //    ถ้าไม่พอ → โยน InsufficientBalanceException ทันที ห้ามให้ balance ติดลบเด็ดขาด
        if (!account.hasSufficientBalance(request.getAmount())) {
            log.warn("Insufficient balance: accountNo={}, balance={}, requested={}",
                    accountNo, account.getBalance(), request.getAmount());
            throw new InsufficientBalanceException(
                    accountNo,
                    account.getBalance(),
                    request.getAmount()
            );
        }

        // 5. บันทึก balance ก่อนทำรายการ
        BigDecimal balanceBefore = account.getBalance();

        // 6. ลดเงิน
        account.setBalance(balanceBefore.subtract(request.getAmount()));

        // 7. save
        accountRepository.save(account);

        // 8. บันทึก Ledger Entry (Immutable Audit Trail)
        LedgerEntry ledgerEntry = LedgerEntry.builder()
                .accountId(accountNo)
                .transactionId(request.getReference() != null ? request.getReference() : "WIT-" + System.currentTimeMillis())
                .operation(LedgerEntry.Operation.DEBIT)
                .amount(request.getAmount())
                .runningBalance(account.getBalance())
                .description("Withdraw")
                .build();
        ledgerEntryRepository.save(ledgerEntry);

        log.info("Withdraw success: accountNo={}, before={}, after={}, ref={}",
                accountNo, balanceBefore, account.getBalance(), request.getReference());

        return AccountDto.TransactionResponse.builder()
                .accountNo(accountNo)
                .transactionType("WITHDRAW")
                .amount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getBalance())
                .reference(request.getReference())
                .transactedAt(LocalDateTime.now())
                .build();
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * ดึงบัญชีแบบ read-only (ไม่ lock)
     */
    private Account findAccountByNo(String accountNo) {
        return accountRepository.findByAccountNo(accountNo)
                .orElseThrow(() -> new AccountNotFoundException(accountNo));
    }

    /**
     * ดึงบัญชีพร้อม PESSIMISTIC_WRITE lock
     * ต้องเรียกภายใน @Transactional เสมอ
     */
    private Account findAccountForUpdate(String accountNo) {
        return accountRepository.findByAccountNoForUpdate(accountNo)
                .orElseThrow(() -> new AccountNotFoundException(accountNo));
    }

    /**
     * ตรวจสอบว่าบัญชีอยู่ในสถานะ ACTIVE
     */
    private void validateAccountIsActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getAccountNo(), account.getStatus());
        }
    }

    /**
     * ตรวจสอบว่าจำนวนเงินมากกว่า 0
     */
    private void validatePositiveAmount(BigDecimal amount, String operation) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                String.format("%s amount must be greater than 0, got: %s", operation, amount)
            );
        }
    }
}
