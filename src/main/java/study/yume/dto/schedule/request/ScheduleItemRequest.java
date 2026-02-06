package study.yume.dto.schedule.request;

import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record ScheduleItemRequest(
        Long id,
        int scheduleOrder,
        Long spotUserId,      // 연결된 개인 장소 ID
        String spotName,
        Double lat,
        Double lng,
        SpotType spotType,
        Boolean isChecked,
        LocalTime startTime,
        int duration,
        LocalTime endTime,
        int movingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
}