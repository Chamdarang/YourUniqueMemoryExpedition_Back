ALTER TABLE spot_visit_history ADD COLUMN schedule_id BIGINT NULL;

-- Old records lack a schedule ID. Pair checked schedules and histories within
-- each user/place/day in a stable order. Preserve all unmatched history rows.
CREATE TEMPORARY TABLE visit_schedule_links AS
SELECT h.id AS history_id, s.id AS schedule_id
FROM (
    SELECT id, user_id, spot_user_id, day_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, spot_user_id, day_id ORDER BY created_at, id
           ) AS visit_order
    FROM spot_visit_history
) h
JOIN (
    SELECT id, user_id, spot_user_id, plan_day_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, spot_user_id, plan_day_id ORDER BY schedule_order, id
           ) AS visit_order
    FROM day_schedule
    WHERE is_checked = 1 AND spot_user_id IS NOT NULL
) s ON s.user_id = h.user_id AND s.spot_user_id = h.spot_user_id
   AND s.plan_day_id = h.day_id AND s.visit_order = h.visit_order;

UPDATE spot_visit_history h
JOIN visit_schedule_links links ON links.history_id = h.id
SET h.schedule_id = links.schedule_id;

DROP TEMPORARY TABLE visit_schedule_links;
CREATE UNIQUE INDEX uq_visit_history_schedule ON spot_visit_history(schedule_id);
