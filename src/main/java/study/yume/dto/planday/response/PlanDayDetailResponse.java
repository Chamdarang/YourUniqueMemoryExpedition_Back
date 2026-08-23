package study.yume.dto.planday.response;

import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.model.PlanDay;
import study.yume.model.enums.ScheduleMode;

import java.util.List;

public record PlanDayDetailResponse(
        Long id,
        String dayName,
        Integer dayOrder,
        String memo,
        ScheduleMode scheduleMode,
        List<DayScheduleResponse> schedules
) {
    public static PlanDayDetailResponse toDto(PlanDay planDay) {
        return new PlanDayDetailResponse(
                planDay.getId(),
                planDay.getDayName(),
                planDay.getDayOrder(),
                planDay.getMemo(),
                planDay.getScheduleMode(),
                planDay.getSchedules().stream()
                        .map(DayScheduleResponse::toDto)
                        .toList()
        );
    }
    public static PlanDayDetailResponse toDto(PlanDay planDay, List<DayScheduleResponse> schedules) {
        return new PlanDayDetailResponse(
                planDay.getId(),
                planDay.getDayName(),
                planDay.getDayOrder(),
                planDay.getMemo(),
                planDay.getScheduleMode(),
                schedules
        );
    }
}
