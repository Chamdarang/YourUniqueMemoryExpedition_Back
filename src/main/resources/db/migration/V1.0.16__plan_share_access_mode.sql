ALTER TABLE plan_share
    ADD COLUMN access_mode VARCHAR(20) NOT NULL DEFAULT 'VIEW' AFTER token_ciphertext;

ALTER TABLE plan_member
    ADD COLUMN share_id BIGINT NULL AFTER invited_by_user_id,
    ADD INDEX idx_plan_member_share_id (share_id),
    ADD CONSTRAINT fk_plan_member_share FOREIGN KEY (share_id) REFERENCES plan_share(id) ON DELETE CASCADE;
