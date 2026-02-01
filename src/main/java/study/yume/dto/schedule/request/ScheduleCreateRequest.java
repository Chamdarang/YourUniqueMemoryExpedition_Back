package study.yume.dto.schedule.request;

import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record ScheduleCreateRequest(
        Long spotId,
        int scheduleOrder,
        LocalTime startTime,
        int duration,
        int movingDuration,
        Transportation transportation,
        String memo,
        String movingMemo

) {
}
