package com.banking.account.repository;

import com.banking.account.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);
    
    List<LedgerEntry> findByTransactionId(String transactionId);
}
