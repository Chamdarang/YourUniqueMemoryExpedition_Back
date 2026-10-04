CREATE TABLE plan_share (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id     BIGINT NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_plan_share_plan UNIQUE (plan_id),
    CONSTRAINT uq_plan_share_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_plan_share_plan FOREIGN KEY (plan_id) REFERENCES plan(id) ON DELETE CASCADE
);
