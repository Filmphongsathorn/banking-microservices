package com.banking.account;

import com.banking.account.dto.AccountDto;
import com.banking.account.entity.Account;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.exception.AccountNotActiveException;
import com.banking.account.exception.DuplicateAccountException;
import com.banking.account.exception.InsufficientBalanceException;
import com.banking.account.repository.AccountRepository;
import com.banking.account.service.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account activeAccount;
    private Account inactiveAccount;

    @BeforeEach
    void setUp() {
        activeAccount = Account.builder()
                .id(1L)
                .accountNo("1234567890")
                .userId(100L)
                .balance(new BigDecimal("5000.0000"))
                .status(Account.AccountStatus.ACTIVE)
                .build();

        inactiveAccount = Account.builder()
                .id(2L)
                .accountNo("9999999999")
                .userId(200L)
                .balance(new BigDecimal("1000.0000"))
                .status(Account.AccountStatus.INACTIVE)
                .build();
    }

    // ─── createAccount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAccount()")
    class CreateAccountTests {

        @Test
        @DisplayName("✅ สร้างบัญชีสำเร็จ")
        void createAccount_success() {
            AccountDto.CreateRequest req = AccountDto.CreateRequest.builder()
                    .accountNo("1234567890")
                    .userId(100L)
                    .initialBalance(new BigDecimal("1000.00"))
                    .build();

            when(accountRepository.existsByAccountNo("1234567890")).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenReturn(activeAccount);

            AccountDto.AccountResponse result = accountService.createAccount(req);

            assertThat(result).isNotNull();
            assertThat(result.getAccountNo()).isEqualTo("1234567890");
            verify(accountRepository, times(1)).save(any(Account.class));
        }

        @Test
        @DisplayName("❌ สร้างบัญชีซ้ำ → DuplicateAccountException")
        void createAccount_duplicate_throwsException() {
            AccountDto.CreateRequest req = AccountDto.CreateRequest.builder()
                    .accountNo("1234567890")
                    .userId(100L)
                    .initialBalance(BigDecimal.ZERO)
                    .build();

            when(accountRepository.existsByAccountNo("1234567890")).thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(req))
                    .isInstanceOf(DuplicateAccountException.class);

            verify(accountRepository, never()).save(any());
        }
    }

    // ─── deposit ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deposit()")
    class DepositTests {

        @Test
        @DisplayName("✅ ฝากเงินสำเร็จ balance เพิ่มขึ้น")
        void deposit_success_balanceIncreases() {
            when(accountRepository.findByAccountNoForUpdate("1234567890"))
                    .thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            AccountDto.DepositRequest req = AccountDto.DepositRequest.builder()
                    .amount(new BigDecimal("1000.00"))
                    .reference("REF-001")
                    .build();

            AccountDto.TransactionResponse result = accountService.deposit("1234567890", req);

            assertThat(result.getTransactionType()).isEqualTo("DEPOSIT");
            assertThat(result.getBalanceBefore()).isEqualByComparingTo("5000.0000");
            assertThat(result.getBalanceAfter()).isEqualByComparingTo("6000.0000");
            assertThat(result.getReference()).isEqualTo("REF-001");
        }

        @Test
        @DisplayName("❌ ฝากเงินเข้าบัญชีที่ไม่ ACTIVE → AccountNotActiveException")
        void deposit_inactiveAccount_throwsException() {
            when(accountRepository.findByAccountNoForUpdate("9999999999"))
                    .thenReturn(Optional.of(inactiveAccount));

            AccountDto.DepositRequest req = AccountDto.DepositRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            assertThatThrownBy(() -> accountService.deposit("9999999999", req))
                    .isInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("❌ หาบัญชีไม่พบ → AccountNotFoundException")
        void deposit_accountNotFound_throwsException() {
            when(accountRepository.findByAccountNoForUpdate("0000000000"))
                    .thenReturn(Optional.empty());

            AccountDto.DepositRequest req = AccountDto.DepositRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            assertThatThrownBy(() -> accountService.deposit("0000000000", req))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    // ─── withdraw ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("✅ ถอนเงินสำเร็จ balance ลดลง")
        void withdraw_success_balanceDecreases() {
            when(accountRepository.findByAccountNoForUpdate("1234567890"))
                    .thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            AccountDto.WithdrawRequest req = AccountDto.WithdrawRequest.builder()
                    .amount(new BigDecimal("2000.00"))
                    .reference("REF-002")
                    .build();

            AccountDto.TransactionResponse result = accountService.withdraw("1234567890", req);

            assertThat(result.getTransactionType()).isEqualTo("WITHDRAW");
            assertThat(result.getBalanceBefore()).isEqualByComparingTo("5000.0000");
            assertThat(result.getBalanceAfter()).isEqualByComparingTo("3000.0000");
        }

        @Test
        @DisplayName("❌ ถอนเงินเกิน balance → InsufficientBalanceException (ห้ามติดลบ)")
        void withdraw_insufficientBalance_throwsException() {
            when(accountRepository.findByAccountNoForUpdate("1234567890"))
                    .thenReturn(Optional.of(activeAccount));

            AccountDto.WithdrawRequest req = AccountDto.WithdrawRequest.builder()
                    .amount(new BigDecimal("9999.00"))  // มากกว่า balance 5000
                    .build();

            assertThatThrownBy(() -> accountService.withdraw("1234567890", req))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");

            // ตรวจสอบว่า save ไม่ถูกเรียก — balance ไม่ถูกแตะ
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("✅ ถอนเงินเท่ากับ balance พอดี → สำเร็จ balance = 0")
        void withdraw_exactBalance_success() {
            when(accountRepository.findByAccountNoForUpdate("1234567890"))
                    .thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            AccountDto.WithdrawRequest req = AccountDto.WithdrawRequest.builder()
                    .amount(new BigDecimal("5000.0000"))  // เท่ากับ balance พอดี
                    .build();

            AccountDto.TransactionResponse result = accountService.withdraw("1234567890", req);

            assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("❌ ถอนเงินจากบัญชีไม่ ACTIVE → AccountNotActiveException")
        void withdraw_inactiveAccount_throwsException() {
            when(accountRepository.findByAccountNoForUpdate("9999999999"))
                    .thenReturn(Optional.of(inactiveAccount));

            AccountDto.WithdrawRequest req = AccountDto.WithdrawRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            assertThatThrownBy(() -> accountService.withdraw("9999999999", req))
                    .isInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("❌ ถอนเงิน 0 บาท → IllegalArgumentException")
        void withdraw_zeroAmount_throwsException() {
            when(accountRepository.findByAccountNoForUpdate("1234567890"))
                    .thenReturn(Optional.of(activeAccount));

            AccountDto.WithdrawRequest req = AccountDto.WithdrawRequest.builder()
                    .amount(BigDecimal.ZERO)
                    .build();

            assertThatThrownBy(() -> accountService.withdraw("1234567890", req))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
