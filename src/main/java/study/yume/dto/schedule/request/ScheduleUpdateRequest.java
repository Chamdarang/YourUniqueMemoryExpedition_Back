package study.yume.dto.schedule.request;

import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record ScheduleUpdateRequest(
        Long spotUserId,
        String spotName,
        Double lat,
        Double lng,
        SpotType spotType,
        LocalTime startTime,
        Boolean fixedStartTime,
        Integer duration,
        LocalTime endTime,
        Integer movingDuration,
        Integer extraDuration,
        Integer extraMovingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
}
