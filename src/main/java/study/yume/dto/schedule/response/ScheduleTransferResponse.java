package study.yume.dto.schedule.response;

import java.util.List;

public record ScheduleTransferResponse(
        Long scheduleId,
        Long sourceDayId,
        Long targetDayId,
        List<DayScheduleResponse> sourceSchedules,
        List<DayScheduleResponse> targetSchedules
) {
}
