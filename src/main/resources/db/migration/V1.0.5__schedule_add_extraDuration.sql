ALTER TABLE day_schedule ADD COLUMN extra_duration INT NOT NULL DEFAULT 0 AFTER moving_duration;
ALTER TABLE day_schedule ADD COLUMN extra_moving_duration INT NOT NULL DEFAULT 0 AFTER extra_duration;

UPDATE day_schedule
SET
    -- memo에서 #si: 숫자 추출하여 extra_duration에 저장
    extra_duration = IF(memo REGEXP '#si:[[:space:]]*[0-9]+',
                        CAST(REGEXP_SUBSTR(memo, '[0-9]+', REGEXP_INSTR(memo, '#si:')) AS UNSIGNED),
                        extra_duration),

    -- moving_memo에서 #mi: 숫자 추출하여 extra_moving_duration에 저장
    extra_moving_duration = IF(moving_memo REGEXP '#mi:[[:space:]]*[0-9]+',
                               CAST(REGEXP_SUBSTR(moving_memo, '[0-9]+', REGEXP_INSTR(moving_memo, '#mi:')) AS UNSIGNED),
                               extra_moving_duration)
WHERE memo REGEXP '#si:[0-9]+' OR moving_memo REGEXP '#mi:[0-9]+';


UPDATE day_schedule
SET
    -- 필요없어진 si ti 메모 제거
    memo = TRIM(REGEXP_REPLACE(memo, '#si:[[:space:]]*[0-9]+', '')),
    moving_memo = TRIM(REGEXP_REPLACE(moving_memo, '#mi:[[:space:]]*[0-9]+', ''))
WHERE memo REGEXP '#si:[0-9]+' OR moving_memo REGEXP '#mi:[0-9]+';