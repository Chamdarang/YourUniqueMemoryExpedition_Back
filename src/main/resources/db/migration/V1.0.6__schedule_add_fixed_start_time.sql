ALTER TABLE day_schedule
    ADD COLUMN fixed_start_time BOOLEAN NOT NULL DEFAULT FALSE AFTER start_time;
