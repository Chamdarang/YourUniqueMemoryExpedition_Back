package study.yume.dto.schedule.request;

import study.yume.model.enums.SpotType;

import java.time.LocalTime;

public record ScheduleCreateRequest(
        int scheduleOrder,
        Long spotUserId,
        String spotName,
        Double lat,
        Double lng,
        SpotType spotType,
        LocalTime startTime,
        String memo
) {
}
