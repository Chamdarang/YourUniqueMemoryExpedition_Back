SET NAMES utf8mb4;
SET time_zone = '+09:00';

ALTER TABLE spot
    ADD COLUMN place_id       VARCHAR(255) NULL AFTER user_id,
    ADD COLUMN short_address  VARCHAR(255) NULL AFTER address,
    ADD COLUMN website        VARCHAR(500) NULL AFTER short_address,
    ADD COLUMN google_map_url VARCHAR(500) NULL AFTER website,
    ADD COLUMN description    TEXT         NULL AFTER location; -- 장소별로 간단히 쓸 공간은 기본적으로 있어야할거같음

CREATE UNIQUE INDEX uq_spot_user_place ON spot (user_id, place_id);