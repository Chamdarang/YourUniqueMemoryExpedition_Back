package study.yume.dto.schedule.response;

import study.yume.model.DaySchedule;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record DayScheduleResponse(
        Long id,
        Long dayId,
        int scheduleOrder,
        Long spotUserId,
        String spotName,      // snapshot
        SpotType spotType,    // snapshot
        Boolean isChecked,
        Double lat,  // snapshot
        Double lng,  // snapshot
        LocalTime startTime,
        boolean fixedStartTime,
        int duration,
        LocalTime endTime,
        int movingDuration,
        int extraDuration,
        int extraMovingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
    public static DayScheduleResponse toDto(DaySchedule schedule) {
        return new DayScheduleResponse(
                schedule.getId(),
                schedule.getPlanDay().getId(),
                schedule.getScheduleOrder(),
                schedule.getSpotUser() != null ? schedule.getSpotUser().getId() : null,
                schedule.getSpotNameSnapshot(),
                schedule.getSpotTypeSnapshot(),
                schedule.getIsChecked(),
                schedule.getSpotLocationSnapshot() != null ? schedule.getSpotLocationSnapshot().getY() : null,
                schedule.getSpotLocationSnapshot() != null ? schedule.getSpotLocationSnapshot().getX() : null,
                schedule.getStartTime(),
                schedule.isFixedStartTime(),
                schedule.getDuration(),
                schedule.getEndTime(),
                schedule.getMovingDuration(),
                schedule.getExtraDuration(),
                schedule.getExtraMovingDuration(),
                schedule.getTransportation(),
                schedule.getMemo(),
                schedule.getMovingMemo()
        );
    }
}
