SET NAMES utf8mb4;
SET time_zone = '+09:00';

ALTER TABLE plan_day
    ADD COLUMN memo       VARCHAR(500) NULL AFTER day_order;