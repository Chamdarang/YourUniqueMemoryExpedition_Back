package study.yume.dto.planday.request;

import study.yume.model.enums.ScheduleMode;

public record PlanDayCreateRequest(
        String dayName,
        Integer dayOrder,
        ScheduleMode scheduleMode
) {
}
