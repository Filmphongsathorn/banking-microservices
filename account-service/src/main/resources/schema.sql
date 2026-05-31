-- ============================================================
--  account_db Schema
--  ใช้ run ครั้งแรก หรือ Flyway migration V1
-- ============================================================

-- สร้าง Database (ถ้ายังไม่มี — run นอก script นี้)
-- CREATE DATABASE account_db;

-- ============================================================
--  Table: accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL       PRIMARY KEY,
    account_no  VARCHAR(20)     NOT NULL,
    user_id     BIGINT          NOT NULL,
    balance     NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- ⚠️  CRITICAL: ป้องกัน balance ติดลบที่ระดับ DB
    --     เป็น safety net อีกชั้นหลัง application layer
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),

    -- account_no ต้องไม่ซ้ำกัน
    CONSTRAINT uq_account_no UNIQUE (account_no),

    -- status ต้องเป็นค่าที่กำหนดไว้เท่านั้น
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- ─── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_accounts_user_id   ON accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status     ON accounts (status);

-- ─── Trigger: auto-update updated_at ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_accounts_updated_at ON accounts;
CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ─── Sample Data (Development Only) ──────────────────────────────────────────
-- INSERT INTO accounts (account_no, user_id, balance, status) VALUES
--     ('1234567890', 1, 10000.0000, 'ACTIVE'),
--     ('0987654321', 2,  5000.0000, 'ACTIVE'),
--     ('1111111111', 3,     0.0000, 'ACTIVE');
