-- BaseEntity @Version column was missing from notification_deliveries in V14.

ALTER TABLE notification_deliveries
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
