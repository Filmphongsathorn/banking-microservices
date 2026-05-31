package com.banking.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account Entity — แทนบัญชีธนาคารของผู้ใช้แต่ละคน
 * ใช้ PESSIMISTIC_WRITE lock ผ่าน Repository เพื่อป้องกัน Race Condition
 */
@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_account_user_id",   columnList = "user_id"),
        @Index(name = "idx_account_no_unique",  columnList = "account_no", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Check(constraints = "balance >= 0")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * หมายเลขบัญชี — unique ห้ามซ้ำ เช่น "1234567890"
     */
    @Column(name = "account_no", nullable = false, unique = true, length = 20)
    private String accountNo;

    /**
     * รหัสผู้ใช้ที่เจ้าของบัญชีนี้ (FK ไป user-service)
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ยอดเงินคงเหลือ — ห้ามติดลบเด็ดขาด
     * ใช้ BigDecimal เพื่อความแม่นยำทางการเงิน
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    private Long version;

    /**
     * สถานะบัญชี: ACTIVE | INACTIVE | SUSPENDED | CLOSED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─── Domain Methods ───────────────────────────────────────────────────────

    /**
     * ตรวจสอบว่าบัญชียังใช้งานได้อยู่หรือไม่
     */
    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }

    /**
     * ตรวจสอบว่าบัญชีมียอดเงินเพียงพอสำหรับจำนวนที่ต้องการถอนหรือไม่
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    // ─── Enum ─────────────────────────────────────────────────────────────────

    public enum AccountStatus {
        ACTIVE,      // บัญชีปกติ ใช้ฝาก/ถอนได้
        INACTIVE,    // บัญชีไม่ได้ใช้งาน
        SUSPENDED,   // บัญชีถูกระงับชั่วคราว
        CLOSED       // บัญชีปิดแล้ว
    }
}
