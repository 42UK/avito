ALTER TABLE notification
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at timestamptz,
    ADD COLUMN error_code varchar(64),
    ADD COLUMN error_message text;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_attempt_count_nonnegative CHECK (attempt_count >= 0);
