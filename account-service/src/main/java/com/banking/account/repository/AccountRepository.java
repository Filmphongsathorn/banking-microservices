package com.banking.account.repository;

import com.banking.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AccountRepository
 *
 * หัวใจสำคัญของการป้องกัน Race Condition คือ findByAccountNoForUpdate()
 * ที่ใช้ PESSIMISTIC_WRITE lock — เมื่อ Transaction A กำลัง lock row อยู่
 * Transaction B ต้องรอให้ A เสร็จก่อน จึงจะดึงข้อมูลและแก้ไขได้
 *
 * ⚠️  ต้องเรียกใช้ภายใน @Transactional เสมอ
 *     มิฉะนั้น Lock จะไม่มีผลและถูก release ทันที
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // ─── READ (ไม่ล็อก) ──────────────────────────────────────────────────────

    /**
     * ค้นหาบัญชีด้วย account_no (อ่านอย่างเดียว ไม่ lock)
     */
    Optional<Account> findByAccountNo(String accountNo);

    /**
     * ค้นหาบัญชีทั้งหมดของ userId หนึ่งคน
     */
    List<Account> findByUserId(Long userId);

    /**
     * เช็คว่า account_no นี้มีอยู่แล้วหรือไม่
     */
    boolean existsByAccountNo(String accountNo);

    // ─── PESSIMISTIC WRITE LOCK ───────────────────────────────────────────────

    /**
     * ดึงข้อมูลบัญชีพร้อม PESSIMISTIC_WRITE lock ด้วย account_no
     *
     * สิ่งที่เกิดขึ้น: Hibernate จะแปลงเป็น SQL ว่า
     *   SELECT * FROM accounts WHERE account_no = ? FOR UPDATE
     *
     * ผลลัพธ์: Row นี้จะถูก lock ไว้จนกว่า Transaction ปัจจุบันจะ commit หรือ rollback
     * Transaction อื่นที่ต้องการ lock row เดียวกันจะต้องรอ
     *
     * ⚠️  ต้องอยู่ใน @Transactional เสมอ
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoForUpdate(@Param("accountNo") String accountNo);

    /**
     * ดึงข้อมูลบัญชีพร้อม PESSIMISTIC_WRITE lock ด้วย id (Primary Key)
     *
     * ใช้เมื่อรู้ id โดยตรง เช่น ใน internal transaction flow
     *
     * ⚠️  ต้องอยู่ใน @Transactional เสมอ
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * ดึงข้อมูลบัญชีพร้อม PESSIMISTIC_WRITE lock ด้วย userId
     * ใช้เมื่อต้องทำ batch operation ในทุก account ของ user คนหนึ่ง
     *
     * ⚠️  ต้องอยู่ใน @Transactional เสมอ
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    List<Account> findByUserIdForUpdate(@Param("userId") Long userId);
}
