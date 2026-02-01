package study.yume.dto.schedule.response;

import study.yume.model.DaySchedule;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record DayScheduleResponse(
        Long id,
        Long dayId,
        int scheduleOrder,
        Long spotId,
        String spotName,
        SpotType spotType,
        Boolean isVisit,
        Double lat,//y
        Double lng,//x
        LocalTime startTime,
        int duration,
        LocalTime endTime,
        int movingDuration,
        Transportation transportation,
        String memo,
        String movingMemo
) {
    public static DayScheduleResponse toDto(DaySchedule schedule) {
        return new DayScheduleResponse(
                schedule.getId(),
                schedule.getPlanDay().getId(),
                schedule.getScheduleOrder(),
                schedule.getSpot() != null ? schedule.getSpot().getId() : null,
                schedule.getSpot() != null ? schedule.getSpot().getSpotName() : null,
                schedule.getSpot() != null ? schedule.getSpot().getSpotType() : null,
                schedule.getSpot() != null ? schedule.getSpot().getIsVisit() : null,
                schedule.getSpot() != null ? schedule.getSpot().getLocation().getY() : null,
                schedule.getSpot() != null ? schedule.getSpot().getLocation().getX() : null,
                schedule.getStartTime(),
                schedule.getDuration(),
                schedule.getEndTime(),
                schedule.getMovingDuration(),
                schedule.getTransportation(),
                schedule.getMemo(),
                schedule.getMovingMemo()
        );
    }
}
