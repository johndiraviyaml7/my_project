-- ============================================================
-- QuantixMed v5 schema delta — PAS / Edge Connector
-- Run once on top of the existing v4 schema.
-- Safe to run multiple times (IF NOT EXISTS).
-- ============================================================

-- 1. Track last heartbeat per device
ALTER TABLE pacs_devices
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- 2. Append-only log of connectivity events for the live dashboard
CREATE TABLE IF NOT EXISTS pacs_status_events (
    id             BIGSERIAL PRIMARY KEY,
    device_id      UUID REFERENCES pacs_devices(id) ON DELETE CASCADE,
    serial_number  VARCHAR(100),
    event_type     VARCHAR(32) NOT NULL,        -- CONNECT, HEARTBEAT, LWT, UPLOAD_STARTED, UPLOAD_COMPLETED, UPLOAD_FAILED
    topic          VARCHAR(255),
    payload        TEXT,
    occurred_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_status_events_device_id   ON pacs_status_events(device_id);
CREATE INDEX IF NOT EXISTS idx_status_events_occurred_at ON pacs_status_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_status_events_serial      ON pacs_status_events(serial_number);
