# Transaction Service 🏦

Production-ready microservice สำหรับระบบธนาคาร  
**Saga Pattern + Idempotency + Kafka Event Publishing**

---

## Architecture Overview

```
Client → TransactionController
           ↓
         TransactionService (Saga Orchestrator)
           ↓
    ┌──────────────────────────────────────┐
    │  Step 1: Redis Idempotency Check     │
    │  Step 2: Create PENDING Transaction  │
    │  Step 3: Debit from_account (Feign)  │
    │  Step 4: Credit to_account (Feign)   │
    │    └── FAIL → Compensate (refund)    │
    │  Step 5: COMPLETED + Kafka Publish   │
    └──────────────────────────────────────┘
```

---

## Tech Stack

| Component       | Technology                          |
|----------------|--------------------------------------|
| Framework       | Spring Boot 3.2 + Java 17           |
| Database        | PostgreSQL 16 (transaction_db)       |
| Cache / Lock    | Redis 7 (Idempotency)               |
| Service Call    | OpenFeign + Resilience4j CB         |
| Messaging       | Apache Kafka (transaction-events)   |
| Migration       | Flyway                               |
| Observability   | Micrometer + Zipkin Tracing         |

---

## API Endpoints

### POST `/api/v1/transactions/transfer`
โอนเงินระหว่างบัญชี

**Request Body:**
```json
{
  "fromAccountId": "uuid",
  "toAccountId": "uuid",
  "amount": 1000.00,
  "idempotencyKey": "client-generated-unique-key-min-16-chars",
  "userId": "uuid",
  "description": "Transfer for rent"
}
```

**Response (201):**
```json
{
  "success": true,
  "message": "Transfer completed successfully",
  "data": {
    "txId": "uuid",
    "status": "COMPLETED",
    "referenceNumber": "TXN17234567...",
    "completedAt": "2025-01-01T12:00:00"
  }
}
```

### GET `/api/v1/transactions/{txId}`
ดูรายละเอียดธุรกรรม

### GET `/api/v1/transactions/account/{accountId}?page=0&size=20`
ประวัติธุรกรรมของบัญชี

### GET `/api/v1/transactions/user/{userId}`
ประวัติธุรกรรมของ user

---

## Saga Pattern Flow

```
Client Request
    │
    ▼
[Redis] Check idempotency-key
    │ ✅ New key → proceed
    │ ❌ Duplicate → return cached result
    │ ⏳ Concurrent → reject (retry later)
    ▼
Save Transaction (PENDING)
    ▼
Feign → account-service: DEBIT from_account
    │ ❌ InsufficientFunds → mark FAILED, return error
    ▼
Feign → account-service: CREDIT to_account
    │ ❌ EXCEPTION → COMPENSATE (refund from_account)
    │   ├── Refund OK → status = COMPENSATED
    │   └── Refund FAIL → status = FAILED (alert ops!)
    ▼
Update status = COMPLETED
    ▼
Redis: store idempotency-key → txId
    ▼
Kafka: publish to 'transaction-events' topic
```

---

## Idempotency Key (Redis)

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `idempotency:{key}` | 24h | Store completed txId |
| `idempotency:processing:{key}` | 5min | SETNX concurrent lock |

---

## Kafka Event

Topic: `transaction-events`  
Key: `txId` (ensures ordering per transaction)

```json
{
  "txId": "uuid",
  "userId": "uuid",
  "fromAccountId": "uuid",
  "toAccountId": "uuid",
  "amount": 1000.00,
  "status": "COMPLETED",
  "eventType": "TRANSFER_COMPLETED",
  "occurredAt": "2025-01-01T12:00:00"
}
```

Event Types:
- `TRANSFER_COMPLETED` — โอนสำเร็จ
- `TRANSFER_COMPENSATED` — โอนล้มเหลว แต่เงินคืนแล้ว
- `TRANSFER_FAILED` — โอนล้มเหลว (critical)

---

## Getting Started

```bash
# 1. Start all dependencies
docker-compose up -d transaction-db redis kafka

# 2. Build
./mvnw clean package -DskipTests

# 3. Run
./mvnw spring-boot:run

# 4. Full stack
docker-compose up -d
```

**Ports:**
- Service: http://localhost:8082
- PostgreSQL: localhost:5433
- Redis: localhost:6379
- Kafka: localhost:9092
- Kafka UI: http://localhost:8090

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | transaction_db | Database name |
| `DB_USERNAME` | txuser | DB username |
| `DB_PASSWORD` | txpassword | DB password |
| `REDIS_HOST` | localhost | Redis host |
| `REDIS_PORT` | 6379 | Redis port |
| `KAFKA_BROKERS` | localhost:9092 | Kafka brokers |
| `ACCOUNT_SERVICE_URL` | http://localhost:8081 | account-service URL |

---

## Running Tests

```bash
./mvnw test
```
