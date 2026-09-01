ALTER TABLE day_schedule
    ADD COLUMN is_skipped BOOLEAN NOT NULL DEFAULT FALSE AFTER is_checked;
