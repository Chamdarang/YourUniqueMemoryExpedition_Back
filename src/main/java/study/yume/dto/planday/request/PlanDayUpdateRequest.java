package study.yume.dto.planday.request;

import study.yume.model.enums.ScheduleMode;

public record PlanDayUpdateRequest(
        String dayName,
        String memo,
        ScheduleMode scheduleMode
) {
}
