-- 개인별 장소
CREATE TABLE IF NOT EXISTS spot_user (
     id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
     user_id                 BIGINT NOT NULL,
     spot_id                 BIGINT NOT NULL,
     spot_type               VARCHAR(40) NOT NULL,
     custom_name             VARCHAR(200) NULL,
     is_visit                TINYINT(1) NOT NULL DEFAULT 0,
     description             TEXT NULL,
     metadata                JSON NULL, -- 정규적으로 들어갈지 모르겠는 우선 넣어보는/넣을 수 있게 할 내용들
     created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 방문 이력
CREATE TABLE IF NOT EXISTS spot_visit_history (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    spot_user_id            BIGINT NOT NULL,
    day_id                  BIGINT NOT NULL,
    plan_id                 BIGINT NULL,
    day_name_snapshot       VARCHAR(200) NOT NULL,
    plan_name_snapshot      VARCHAR(200) NULL,
    visited_at              DATE NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 마이그레이션 spot->spot,spout_user
INSERT INTO spot_user (user_id, spot_id, custom_name, spot_type, is_visit, description, created_at)
SELECT user_id, id, spot_name, spot_type, is_visit, description,created_at FROM spot;

-- 인덱스 제거
DROP INDEX idx_sgm_group_spot ON spot_group_map;
DROP INDEX idx_spotpurchase_user_spot_kind_status ON spot_purchase;
DROP INDEX idx_spot_user_type ON spot;
DROP INDEX idx_spot_user_isvisit ON spot;
DROP INDEX uq_spot_user_place ON spot;
DROP INDEX idx_schedule_user ON day_schedule; -- 필요없음

-- spot_id에서 spot_user_id로 변경
ALTER TABLE spot_group_map ADD COLUMN  spot_user_id BIGINT NOT NULL;
ALTER TABLE day_schedule ADD COLUMN  spot_user_id BIGINT NULL; -- 임시등록일정은 이게 없음
ALTER TABLE spot_purchase ADD COLUMN  spot_user_id BIGINT NOT NULL;

-- spot_group_map 데이터 보정
UPDATE spot_group_map sgm
    JOIN spot_group sg ON sgm.group_id = sg.id
    JOIN spot_user su ON su.user_id = sg.user_id AND su.spot_id = sgm.spot_id
SET sgm.spot_user_id = su.id;

UPDATE day_schedule ds
    JOIN spot_user su ON ds.user_id = su.user_id AND ds.spot_id = su.spot_id
SET ds.spot_user_id = su.id;

-- spot_purchase 데이터 보정
UPDATE spot_purchase sp
    JOIN spot_user su ON sp.user_id = su.user_id AND sp.spot_id = su.spot_id
SET sp.spot_user_id = su.id;

ALTER TABLE spot_group_map DROP COLUMN spot_id;
ALTER TABLE day_schedule DROP COLUMN spot_id;
ALTER TABLE spot_purchase DROP COLUMN spot_id;




-- spot 테이블 구조 변경
ALTER TABLE spot DROP COLUMN user_id;
ALTER TABLE spot DROP COLUMN spot_type;
ALTER TABLE spot DROP COLUMN is_visit;
ALTER TABLE spot DROP COLUMN visit_date;
ALTER TABLE spot DROP COLUMN description;

-- day_schedule 스냅샷 컬럼 추가
ALTER TABLE day_schedule ADD COLUMN spot_name_snapshot VARCHAR(200) NULL AFTER spot_user_id;
ALTER TABLE day_schedule ADD COLUMN spot_location_snapshot POINT NULL SRID 4326 AFTER spot_name_snapshot;
ALTER TABLE day_schedule ADD COLUMN spot_type_snapshot VARCHAR(40) NULL AFTER spot_location_snapshot;
ALTER TABLE day_schedule ADD COLUMN is_checked TINYINT(1) NOT NULL DEFAULT 0 AFTER spot_type_snapshot; -- 이번 여행에서 들렀는지

-- 인덱스 추가
CREATE UNIQUE INDEX uq_spot_place_id ON spot (place_id);
CREATE UNIQUE INDEX uq_spot_user_spot ON spot_user (user_id, spot_id);
CREATE INDEX idx_spotuser_spot_id ON spot_user (spot_id);
CREATE INDEX idx_spotuser_user_type ON spot_user(user_id, spot_type);
CREATE INDEX idx_spotuser_user_isvisit ON spot_user(user_id, is_visit);
CREATE INDEX idx_sgm_group_spotuser ON spot_group_map(group_id, spot_user_id);
CREATE INDEX idx_sgm_spotuser_group ON spot_group_map(spot_user_id, group_id);
CREATE INDEX idx_spotpurchase_user_spotuser_kind_status ON spot_purchase(user_id,spot_user_id,kind,status);
CREATE INDEX idx_visithistory_user_spot ON spot_visit_history (user_id, spot_id);
CREATE INDEX idx_visithistory_user_day ON spot_visit_history (user_id, day_id);
