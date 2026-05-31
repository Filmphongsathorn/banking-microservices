-- V1__Create_transactions_table.sql
-- Transaction Service - Initial Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE transaction_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
    'COMPENSATING',
    'COMPENSATED'
);

CREATE TABLE transactions (
    tx_id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    from_account_id    UUID            NOT NULL,
    to_account_id      UUID            NOT NULL,
    amount             NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    idempotency_key    VARCHAR(255)    NOT NULL UNIQUE,
    reference_number   VARCHAR(100),
    description        VARCHAR(500),
    failure_reason     VARCHAR(1000),
    user_id            UUID,
    compensated_tx_id  UUID,
    created_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMP
);

-- Indexes สำหรับ query performance
CREATE INDEX idx_transactions_from_acc     ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_acc       ON transactions(to_account_id);
CREATE INDEX idx_transactions_status       ON transactions(status);
CREATE INDEX idx_transactions_user_id      ON transactions(user_id);
CREATE INDEX idx_transactions_created_at   ON transactions(created_at DESC);
CREATE UNIQUE INDEX idx_transactions_idempotency ON transactions(idempotency_key);

-- Auto-update updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Comments
COMMENT ON TABLE transactions IS 'Core transaction records for fund transfers';
COMMENT ON COLUMN transactions.idempotency_key IS 'Client-provided key to prevent duplicate transactions';
COMMENT ON COLUMN transactions.status IS 'PENDING→PROCESSING→COMPLETED or FAILED/COMPENSATING/COMPENSATED';
COMMENT ON COLUMN transactions.compensated_tx_id IS 'Points to compensation transaction if this tx was reversed';
