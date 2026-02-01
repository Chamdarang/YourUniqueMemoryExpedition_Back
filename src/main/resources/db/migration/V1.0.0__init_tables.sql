SET NAMES utf8mb4;
SET time_zone = '+09:00';

CREATE TABLE IF NOT EXISTS users (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    username                VARCHAR(100)  NOT NULL UNIQUE,
    user_password           VARCHAR(255)  NOT NULL,
    role                    VARCHAR(50)   NOT NULL
);

-- 유저가 장소를 추가하는 그룹('현존천수','도쿄에 있는 절신사', '도호쿠' 등)
CREATE TABLE IF NOT EXISTS spot_group(
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    group_name              VARCHAR(100) NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spot(
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    spot_name               VARCHAR(200) NOT NULL,
    spot_type               VARCHAR(40) NOT NULL,
    address                 VARCHAR(255) NOT NULL,
    location                POINT NOT NULL SRID 4326,
    is_visit                TINYINT(1) NOT NULL DEFAULT 0,

    visit_date              DATE NULL,
    metadata                JSON NOT NULL,

    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spot_group_map (
    spot_id                 BIGINT NOT NULL,
    group_id                BIGINT NOT NULL,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY(spot_id, group_id)
);

-- 기념품 존재/구매 여부
CREATE TABLE IF NOT EXISTS spot_purchase (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    spot_id                 BIGINT NOT NULL,

    -- 대분류(GOSHUIN, GOSHUINCHO, SOUVENIR 등)
    kind                    VARCHAR(30) NOT NULL,

    -- 기념품 세부분류(MAGNET, KEYRING 등, 아예 없어도 될 수 있음)
    category                VARCHAR(40) NULL,

    -- 이름(icsca, 히메지 스탬프 가챠 등) 자유기입
    item_name               VARCHAR(200) NULL,

    -- 모름 UNKNOWN / 있음 AVAILABLE / 원함 WANT / 구했음 ACQUIRED / 안 원함 SKIPPED / 못 구함 UNAVAILABLE
    status                  VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    quantity                INT NOT NULL DEFAULT 1,


    price                   DECIMAL(13,2) NULL, -- 필요시 기입
    currency                CHAR(3) NULL, -- 'JPY' 등, 필요시 기입
    acquired_date           DATE NULL, -- 구한 날짜
    note                    VARCHAR(500) NULL,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plan (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    plan_name               VARCHAR(200) NOT NULL,
    plan_start_date         DATE NULL, -- 아직 안 정했을 수 있음
    plan_days               INT NULL, -- 아직 안 정했을 수 있음
    plan_end_date           DATE NULL, -- 앞에서 안 정해졌으면 없음
    plan_memo               VARCHAR(500) NULL,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plan_day (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    plan_id                 BIGINT NULL,

    day_name                VARCHAR(200) NOT NULL,
    day_order               INT NOT NULL DEFAULT 1,

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS day_schedule (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT NOT NULL,
    plan_day_id             BIGINT NOT NULL,

    schedule_order          INT NOT NULL,

    spot_id                 BIGINT NULL,
    start_time              TIME NULL,
    duration                INT NULL, -- 체류/소요시간, 단위: 분
    end_time                TIME NULL,

    moving_duration         INT NULL,   -- 이전 장소로부터의 이동시간, 단위: 분
    transportation          VARCHAR(50) NULL,

    memo                    VARCHAR(500) NULL,
    moving_memo             VARCHAR(500) NULL,


    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_spotgroup_user_name ON spot_group (user_id, group_name);

CREATE INDEX idx_spot_user_type ON spot(user_id, spot_type);
CREATE INDEX idx_spot_user_isvisit ON spot(user_id, is_visit);

CREATE INDEX idx_sgm_group_spot ON spot_group_map (group_id, spot_id);

-- 특정 범위(목록) 장소들 중 특정 종류(goshuin)가 특정 상태(want)인 것만
CREATE INDEX idx_spotpurchase_user_spot_kind_status ON spot_purchase(user_id,spot_id,kind,status);
-- 특정 종류(souvenir)의 특정 상태(want)인 것만
CREATE INDEX idx_spotpurchase_user_kind_status ON spot_purchase(user_id,kind,status);

CREATE SPATIAL INDEX idx_spot_location ON spot (location);

CREATE INDEX idx_plan_user ON plan(user_id);
CREATE INDEX idx_planday_user ON plan_day(user_id);
CREATE INDEX idx_planday_plan ON plan_day(plan_id);
CREATE INDEX idx_schedule_user ON day_schedule(user_id);

-- 플랜의 각 날짜 일정순으로
CREATE INDEX idx_schedule_day_order ON day_schedule(plan_day_id, schedule_order);