package study.yume.dto.planday.response;

import study.yume.model.PlanDay;
import study.yume.model.enums.ScheduleMode;


public record PlanDayResponse(
        Long id,
        String dayName,
        Integer dayOrder,
        String memo,
        ScheduleMode scheduleMode
) {
    public static PlanDayResponse toDto(PlanDay planDay) {
        return new PlanDayResponse(
                planDay.getId(),
                planDay.getDayName(),
                planDay.getDayOrder(),
                planDay.getMemo(),
                planDay.getScheduleMode()
        );
    }
}
