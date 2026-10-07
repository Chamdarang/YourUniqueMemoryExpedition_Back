CREATE TABLE plan_member (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id             BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    invited_by_user_id  BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_plan_member_user_status (user_id, status),
    CONSTRAINT uq_plan_member_plan_user UNIQUE (plan_id, user_id),
    CONSTRAINT fk_plan_member_plan FOREIGN KEY (plan_id) REFERENCES plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
