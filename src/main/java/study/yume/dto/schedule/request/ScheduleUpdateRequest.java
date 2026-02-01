package study.yume.dto.schedule.request;

import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record ScheduleUpdateRequest(
        Long spotId,
        Long dayId,
        Integer scheduleOrder,
        LocalTime startTime,
        Integer duration,
        Integer movingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
}
