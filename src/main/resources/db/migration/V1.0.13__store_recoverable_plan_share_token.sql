ALTER TABLE plan_share
    ADD COLUMN token_ciphertext VARCHAR(255) NULL AFTER token_hash;
