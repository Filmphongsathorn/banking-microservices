package com.banking.account.job;

import com.banking.account.entity.Account;
import com.banking.account.entity.LedgerEntry;
import com.banking.account.repository.AccountRepository;
import com.banking.account.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReconciliationJob {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * รันทุกเที่ยงคืน (00:00:00) ของทุกวัน
     * เพื่อตรวจสอบ (Reconcile) ว่ายอด balance ของทุกบัญชีตรงกับ Sum ของ Ledger Entry ในวันนั้นหรือไม่
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void runDailyReconciliation() {
        log.info("[EOD-RECONCILIATION] Starting Daily Reconciliation Job...");
        
        LocalDateTime startOfDay = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        List<Account> allAccounts = accountRepository.findAll();
        int discrepancyCount = 0;

        for (Account account : allAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(account.getAccountId());
            
            // คำนวณหายอดรวมจาก Ledger
            BigDecimal calculatedBalance = BigDecimal.ZERO;
            for (LedgerEntry entry : entries) {
                if ("CREDIT".equalsIgnoreCase(entry.getOperation())) {
                    calculatedBalance = calculatedBalance.add(entry.getAmount());
                } else if ("DEBIT".equalsIgnoreCase(entry.getOperation())) {
                    calculatedBalance = calculatedBalance.subtract(entry.getAmount());
                }
            }

            if (calculatedBalance.compareTo(account.getBalance()) != 0) {
                discrepancyCount++;
                log.error("[EOD-RECONCILIATION] DISCREPANCY DETECTED! AccountId: {}, Stored Balance: {}, Calculated Ledger Balance: {}", 
                        account.getAccountId(), account.getBalance(), calculatedBalance);
                
                // TODO: พ่น Alert เข้า Kafka ให้ Ops Team ตรวจสอบด่วน
            }
        }

        if (discrepancyCount == 0) {
            log.info("[EOD-RECONCILIATION] Job Completed. All {} accounts reconciled perfectly. No discrepancies found.", allAccounts.size());
        } else {
            log.error("[EOD-RECONCILIATION] Job Completed with {} discrepancies!", discrepancyCount);
        }
    }
}
