-- init-db.sql: สร้าง Schema เริ่มต้นสำหรับ notification_db

CREATE TABLE IF NOT EXISTS notification_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    phone_number    VARCHAR(20),
    message         VARCHAR(500)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    notification_type VARCHAR(50),
    transaction_id  VARCHAR(100),
    amount          NUMERIC(19, 2),
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    failure_reason  VARCHAR(255),
    retry_count     INTEGER         DEFAULT 0
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_notification_user_id      ON notification_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status       ON notification_logs(status);
CREATE INDEX IF NOT EXISTS idx_notification_sent_at      ON notification_logs(sent_at);
CREATE INDEX IF NOT EXISTS idx_notification_transaction  ON notification_logs(transaction_id);

-- Initial comment
COMMENT ON TABLE notification_logs IS 'ประวัติการส่ง SMS แจ้งเตือนธุรกรรมธนาคาร';
