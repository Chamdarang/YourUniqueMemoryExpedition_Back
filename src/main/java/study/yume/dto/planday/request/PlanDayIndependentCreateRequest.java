package study.yume.dto.planday.request;

import study.yume.model.enums.ScheduleMode;

public record PlanDayIndependentCreateRequest(
        String dayName,
        ScheduleMode scheduleMode
) {
}
