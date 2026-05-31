# account-service

**Banking Core Service** — จัดการบัญชีธนาคาร (deposit, withdraw) ด้วย PESSIMISTIC_WRITE lock เพื่อป้องกัน Race Condition

---

## Tech Stack

| Component   | Technology                |
|-------------|---------------------------|
| Framework   | Spring Boot 3.2.5         |
| Java        | 17                        |
| Database    | PostgreSQL (account_db)   |
| Port        | 8082                      |

---

## Architecture Overview

```
account-service
├── entity/          Account.java (JPA Entity)
├── repository/      AccountRepository.java (PESSIMISTIC_WRITE lock)
├── service/         AccountService + AccountServiceImpl
├── controller/      AccountController (REST API)
├── dto/             AccountDto, ApiResponse
├── exception/       InsufficientBalanceException, AccountNotFoundException, ...
└── config/          JpaConfig, GlobalExceptionHandler
```

---

## PESSIMISTIC_WRITE Lock — การป้องกัน Race Condition

```
Thread A (Withdraw 3000)         Thread B (Withdraw 3000) [balance=5000]
────────────────────────────     ──────────────────────────────────────
BEGIN TRANSACTION
SELECT ... FOR UPDATE            BEGIN TRANSACTION
(lock acquired ✅)               SELECT ... FOR UPDATE
balance=5000 ✓ พอ                (⛔ blocked — รอ Thread A)
balance = 5000 - 3000 = 2000
COMMIT (lock released)
                                 (lock acquired ✅)
                                 balance=2000 ❌ ไม่พอ!
                                 → InsufficientBalanceException
                                 ROLLBACK
```

โดยไม่มี lock: Thread B อาจอ่าน balance=5000 พร้อมกับ A และถอนเงินซ้ำซ้อน ทำให้ balance ติดลบ

---

## Quick Start

### 1. เตรียม PostgreSQL

```sql
CREATE DATABASE account_db;
```

รัน `src/main/resources/schema.sql` เพื่อสร้าง table และ constraints

### 2. ตั้งค่า Environment Variables

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
```

หรือแก้ใน `application.yml` โดยตรง

### 3. Run

```bash
mvn spring-boot:run
```

---

## API Endpoints

Base URL: `http://localhost:8082/api/v1/accounts`

### สร้างบัญชีใหม่
```
POST /api/v1/accounts
Content-Type: application/json

{
  "accountNo": "1234567890",
  "userId": 1,
  "initialBalance": 1000.00
}
```

### ดูข้อมูลบัญชี
```
GET /api/v1/accounts/{accountNo}
```

### ดูบัญชีทั้งหมดของ User
```
GET /api/v1/accounts/user/{userId}
```

### ฝากเงิน
```
POST /api/v1/accounts/{accountNo}/deposit
Content-Type: application/json

{
  "amount": 500.00,
  "reference": "TXN-001"
}
```

### ถอนเงิน
```
POST /api/v1/accounts/{accountNo}/withdraw
Content-Type: application/json

{
  "amount": 300.00,
  "reference": "TXN-002"
}
```

**Error Response (ยอดเงินไม่พอ — HTTP 422):**
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Insufficient balance on account [1234567890]: current=200.0000, requested=300.0000"
  },
  "data": {
    "accountNo": "1234567890",
    "currentBalance": 200.0000,
    "requestedAmount": 300.0000
  }
}
```

---

## HTTP Status Codes

| Code | Situation                        |
|------|----------------------------------|
| 200  | Success                          |
| 201  | Created (new account)            |
| 400  | Validation error / bad request   |
| 404  | Account not found                |
| 409  | Duplicate account / not active   |
| 422  | Insufficient balance             |
| 500  | Internal server error            |

---

## Run Tests

```bash
mvn test
```
