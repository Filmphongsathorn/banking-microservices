# 🏦 Next-Generation Core Banking Microservices Architecture

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/Kafka-Event%20Driven-black.svg" alt="Kafka">
  <img src="https://img.shields.io/badge/React-Frontend-61dafb.svg" alt="React">
  <img src="https://img.shields.io/badge/Docker-Containerized-2496ed.svg" alt="Docker">
</p>

> **"A portfolio-grade, event-driven banking platform engineered for massive scale, zero-downtime, and high consistency."**

This project demonstrates a state-of-the-art **Microservices Architecture** built to enterprise standards. It moves away from monolithic legacy banking systems into a highly scalable, resilient, and event-driven ecosystem. Every component is meticulously designed to handle thousands of concurrent transactions with absolute data integrity using the **Database-per-Service** pattern, **Event Sourcing**, and **Idempotent API** designs.

---

## 🛠️ Technology Stack (Enterprise Grade)

- **Frontend Interface:** React 18 (Vite), TailwindCSS, Radix UI (Sleek, responsive, light-theme UI)
- **Backend Microservices:** Java 21, Spring Boot 3.2, Spring Data JPA, Hibernate
- **Infrastructure & Routing:** Spring Cloud API Gateway, Spring Cloud Eureka (Service Registry), Spring Cloud Config Server
- **Event-Driven Backbone:** Apache Kafka, Apache Zookeeper
- **Data Layer:** PostgreSQL 16 (Relational), Redis 7.2 (Caching & Distributed Locks)
- **Observability & Monitoring:** Prometheus, Grafana, ELK Stack (Elasticsearch, Logstash, Kibana), Kafka UI, pgAdmin
- **Containerization & DevOps:** Docker, Docker Compose (Multi-profile environments)

---

## 🏗️ Architecture Overview

The system is built on a highly decoupled architecture. No two microservices share the same database, completely eliminating single points of failure and database-level locks.

```text
                           ┌─────────────────────────────────────────────┐
   Client ──► :8080        │              API Gateway                    │
   (Web/Mobile)            │  JWT Validation · Rate Limit · Aggregation  │
                           └──────┬──────┬──────┬──────┬──────┬──────────┘
                                  │      │      │      │      │
                      ┌──────────┐│ ┌────┐│ ┌────┐│ ┌──────┐│ ┌──────────┐
                      │  Auth    ││ │Prof││ │Acct││ │ Txn  ││ │  Notif   │
                      │  :8081   ││ │:82 ││ │:83 ││ │ :84  ││ │  :8085   │
                      └────┬─────┘│ └──┬─┘│ └──┬─┘│ └──┬───┘│ └────┬─────┘
                           │      │    │   │    │  │    │    │      │
                ┌──────────▼──────▼────▼───▼────▼──▼────▼────▼──────▼──┐
                │             Event-Driven Middleware Layer            │
                │  PostgreSQL :5432 │ Redis :6379 │ Apache Kafka :9092 │
                └──────────────────────────────────────────────────────┘
```

---

## ⚙️ How It Works: The 0-100 Lifecycle (Saga Pattern & Event Driven)

To truly appreciate the architecture, here is the lifecycle of a **Money Transfer**, demonstrating how the microservices collaborate asynchronously:

1. **The Request:** The user submits a transfer via the React Frontend.
2. **Gateway Interception:** The Spring Cloud API Gateway intercepts the request, validates the JWT token with the Auth Service, applies rate limiting (Redis), and forwards it to the Transaction Service.
3. **Idempotency Check:** The Transaction Service checks **Redis** to ensure this exact transaction hasn't been processed in the last 24 hours (preventing double-spending from double-clicks).
4. **Pending State:** The Transaction Service saves the transaction as `PENDING` in `transaction_db`.
5. **Event Publication:** It publishes a `transaction.initiated` event to **Apache Kafka**.
6. **Async Processing:** The Account Service consumes the Kafka event. It acquires a distributed lock, deducts the balance from the sender, adds to the receiver, and commits to `account_db`.
7. **Resolution:** The Account Service fires a `transaction.completed` (or `failed`) event back into Kafka.
8. **Finalization:** The Transaction Service hears the completion event and marks the record as `SUCCESS`.
9. **Notification:** Simultaneously, the Notification Service consumes the completion event and dispatches real-time SMS/Email alerts to both the sender and receiver.

All of this happens in milliseconds, ensuring **Eventual Consistency** and decoupling heavy database I/O from the user's API request thread.

---

## 🗄️ Database Architecture (Database-per-Service)

To prevent cascading failures, the system provisions 5 logically isolated databases inside PostgreSQL:

| Database | Microservice | What it stores (Schema Highlights) |
|---|---|---|
| `auth_db` | **Auth Service** | Stores User Credentials (hashed via BCrypt), Roles, JWT Refresh Tokens, and account lock-out statuses. |
| `profile_db` | **Profile Service** | Stores KYC (Know Your Customer) data: First Name, Last Name, Phone, Address, Date of Birth. |
| `account_db` | **Account Service** | The financial ledger. Stores Account Numbers, Balances, Account Types (SAVING/CHECKING), and active statuses. |
| `transaction_db`| **Transaction Service**| The audit trail. Stores Transfer Records, Amounts, Timestamps, Sender/Receiver IDs, Idempotency Keys, and states (PENDING/SUCCESS). |
| `notification_db`| **Notification Service**| Stores Notification Logs, Email/SMS templates, delivery statuses, and retry queues. |

---

## 🚀 Quick Start (Plug & Play)

No complex configuration required. The entire distributed system can be spun up with a single command.

**Prerequisites:** Docker Desktop installed and running.

### Step 1: Download & Open
Clone or Download the ZIP of this repository. Extract it, and open your terminal (PowerShell / Command Prompt) inside the extracted folder.

### Step 2: Ignite the Platform
Run the following command to download all dependencies, compile the Java code, and start the infrastructure:

```bash
docker-compose --profile heavy-ops --profile observability up -d --build
```
*(Grab a coffee ☕. The first run takes 2-3 minutes as Docker builds the ecosystem).*

### Step 3: Access the System
Once the terminal returns to you, the banking system is fully operational.

---

## 🎛️ Control Panel Access

As an administrator or developer, you have access to the ultimate DevOps dashboard suite:

### 🌐 1. The Banking Web Application (Frontend)
👉 **URL:** [http://localhost:5173](http://localhost:5173)
- **What it is:** The beautiful, minimalist Light-Theme user interface built with React. This is what the end-users see. They can register, view balances, and transfer money.

### 📊 2. System Analytics Dashboard (Grafana)
👉 **URL:** [http://localhost:3000](http://localhost:3000)
- **Credentials:** User: `admin` / Password: `admin`
- **What it is:** The heart of system observability. Grafana reads metrics from Prometheus to show you real-time graphs of CPU usage, API traffic, database query times, and error rates across all microservices.

### 🐘 3. Message Queue Monitor (Kafka UI)
👉 **URL:** [http://localhost:9091](http://localhost:9091)
- **What it is:** The nervous system visualizer. Here you can watch events (like `transaction.completed`) fly between microservices in real-time. It's the ultimate tool for debugging distributed architectures.

### 🗄️ 4. Database Administrator (pgAdmin)
👉 **URL:** [http://localhost:5050](http://localhost:5050)
- **Credentials:** User: `admin@bank.com` / Password: `changeme`
- **What it is:** The visual database manager. Log in here to manually inspect the raw tables of all 5 microservice databases (`auth_db`, `account_db`, etc.) without writing SQL in the terminal.

---

## 🛡️ Production Readiness & Security
- **API Security:** All microservice endpoints are protected behind the API Gateway. Direct access is firewalled off.
- **JWT Authentication:** Stateless, signed tokens ensure zero-session overhead.
- **Rate Limiting:** Redis-backed rate limiters prevent DDoS attacks and brute-force login attempts.
- **Circuit Breakers:** Resilience4j prevents cascading failures if a downstream service goes offline.

---
*Engineered with precision for the modern cloud.*
