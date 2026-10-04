ALTER TABLE plan_share
    ADD COLUMN expires_at DATETIME(6) NULL AFTER revoked_at;
