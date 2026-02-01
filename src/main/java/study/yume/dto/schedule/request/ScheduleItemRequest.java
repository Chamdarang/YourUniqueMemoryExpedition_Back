package study.yume.dto.schedule.request;

import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record ScheduleItemRequest(
        Long id,
        int scheduleOrder,
        Long spotId,
        LocalTime startTime,
        int duration,
        LocalTime endTime,
        int movingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
}
