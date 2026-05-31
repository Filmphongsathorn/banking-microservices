package com.banking.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LedgerEntry Entity - ระบบสมุดบัญชีแยกประเภท (Double-Entry Ledger)
 * ทุกการเปลี่ยนแปลงของยอดเงิน (Balance) ต้องถูกบันทึกลง Ledger อย่างถาวร
 * ห้ามมีการ UPDATE หรือ DELETE ข้อมูลในตารางนี้เด็ดขาด (Immutable)
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_account_id", columnList = "account_id"),
        @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 20)
    private String accountId;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private Operation operation;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // ยอดเงินหลังจากทำรายการเสร็จ
    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Operation {
        CREDIT, DEBIT
    }
}
