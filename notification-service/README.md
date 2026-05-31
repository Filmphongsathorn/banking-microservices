# 🔔 Notification Service — Phase 5

ระบบแจ้งเตือน SMS สำหรับระบบธนาคาร รับ Event จาก Kafka แล้วส่ง SMS และบันทึก Log ลง PostgreSQL

---

## 🏗️ Architecture

```
transaction-service
      │
      │ (Kafka Topic: transaction-events)
      ▼
TransactionEventListener (Kafka Consumer)
      │
      ▼
NotificationService
  ├── SmsService (Simulator)
  └── NotificationLogRepository (notification_db / PostgreSQL)
```

---

## 📦 โครงสร้างไฟล์

```
notification-service/
├── src/main/java/com/bank/notification/
│   ├── NotificationServiceApplication.java   ← Main class
│   ├── entity/
│   │   └── NotificationLog.java              ← JPA Entity
│   ├── repository/
│   │   └── NotificationLogRepository.java    ← Spring Data JPA
│   ├── dto/
│   │   ├── TransactionEvent.java             ← Kafka Message DTO
│   │   └── SmsResult.java                    ← SMS Result DTO
│   ├── consumer/
│   │   └── TransactionEventListener.java     ← Kafka Listener ⭐
│   ├── service/
│   │   ├── NotificationService.java          ← Business Logic ⭐
│   │   └── SmsService.java                   ← SMS Simulator ⭐
│   ├── config/
│   │   ├── KafkaConsumerConfig.java          ← Kafka Config
│   │   └── WebConfig.java                    ← CORS Config
│   └── controller/
│       └── NotificationController.java       ← REST API
├── src/main/resources/
│   └── application.yml
├── docker-compose.yml
├── Dockerfile
├── init-db.sql
└── pom.xml
```

---

## 🚀 วิธีรัน

### Option 1: Docker Compose (แนะนำ)

```bash
# รัน PostgreSQL + Kafka + Notification Service พร้อมกัน
docker-compose up -d

# ดู logs
docker-compose logs -f notification-service
```

### Option 2: Local Development

```bash
# 1. รัน PostgreSQL และ Kafka ก่อน (ผ่าน Docker)
docker-compose up -d notification-db zookeeper kafka

# 2. รัน Spring Boot
mvn spring-boot:run
```

---

## 🧪 ทดสอบ

### 1. ทดสอบผ่าน REST API (ไม่ต้องผ่าน Kafka)

```bash
curl -X POST http://localhost:8083/api/v1/notifications/test/transfer-success \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "TRANSFER_SUCCESS",
    "transactionId": "TXN-20240101-001",
    "senderId": 1,
    "receiverId": 2,
    "amount": 5000.00,
    "currency": "THB",
    "status": "SUCCESS",
    "transactionTime": "2024-01-01T10:00:00",
    "senderRemainingBalance": 45000.00
  }'
```

### 2. ทดสอบผ่าน Kafka (ส่ง Message จริง)

```bash
# เปิด Kafka Producer
docker exec -it kafka kafka-console-producer \
  --topic transaction-events \
  --bootstrap-server localhost:9092

# พิมพ์ JSON นี้แล้วกด Enter:
{"eventType":"TRANSFER_SUCCESS","transactionId":"TXN-TEST-001","senderId":1,"receiverId":2,"amount":1000.00,"currency":"THB","status":"SUCCESS","transactionTime":"2024-01-01T10:00:00","senderRemainingBalance":9000.00}
```

### 3. ดูประวัติการแจ้งเตือน

```bash
# ดูทั้งหมดของ user 1
curl http://localhost:8083/api/v1/notifications/user/1

# ดูสถิติ
curl http://localhost:8083/api/v1/notifications/stats

# ดูตาม transactionId
curl http://localhost:8083/api/v1/notifications/transaction/TXN-TEST-001
```

---

## 📋 Entity: NotificationLog

| Column | Type | Description |
|---|---|---|
| id | BIGINT | Primary Key |
| user_id | BIGINT | ID ผู้ใช้ |
| phone_number | VARCHAR(20) | เบอร์โทร |
| message | VARCHAR(500) | ข้อความ SMS |
| status | ENUM | PENDING / SENT / FAILED |
| notification_type | ENUM | TRANSFER_SUCCESS / TRANSFER_FAILED |
| transaction_id | VARCHAR(100) | อ้างอิง Transaction |
| amount | NUMERIC(19,2) | จำนวนเงิน |
| sent_at | TIMESTAMP | เวลาที่ส่ง |
| created_at | TIMESTAMP | เวลาสร้าง Record |
| failure_reason | VARCHAR(255) | เหตุผลที่ล้มเหลว |
| retry_count | INTEGER | จำนวนครั้งที่ retry |

---

## ⚙️ Configuration

| Property | Default | Description |
|---|---|---|
| server.port | 8083 | Port ของ Service |
| spring.datasource.url | localhost:5432/notification_db | PostgreSQL |
| spring.kafka.bootstrap-servers | localhost:9092 | Kafka |
| notification.sms.provider | SIMULATOR | SMS Provider |
