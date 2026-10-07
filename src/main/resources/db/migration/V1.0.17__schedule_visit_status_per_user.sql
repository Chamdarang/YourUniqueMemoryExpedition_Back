CREATE TABLE schedule_visit_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    spot_id BIGINT NULL,
    spot_link_cleared TINYINT(1) NOT NULL DEFAULT 0,
    is_checked TINYINT(1) NOT NULL DEFAULT 0,
    is_skipped TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_schedule_visit_user UNIQUE (schedule_id, user_id),
    CONSTRAINT fk_schedule_visit_schedule FOREIGN KEY (schedule_id)
        REFERENCES day_schedule (id) ON DELETE CASCADE,
    INDEX idx_schedule_visit_user (user_id)
);

INSERT INTO schedule_visit_status (schedule_id, user_id, spot_id, is_checked, is_skipped)
SELECT ds.id, ds.user_id, su.spot_id, ds.is_checked, ds.is_skipped
FROM day_schedule ds
LEFT JOIN spot_user su ON su.id = ds.spot_user_id
WHERE ds.is_checked = 1 OR ds.is_skipped = 1 OR su.spot_id IS NOT NULL;

DROP INDEX uq_visit_history_schedule ON spot_visit_history;
CREATE UNIQUE INDEX uq_visit_history_user_schedule ON spot_visit_history (user_id, schedule_id);
