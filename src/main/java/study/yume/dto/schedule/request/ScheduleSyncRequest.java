package study.yume.dto.schedule.request;

import java.util.List;

public record ScheduleSyncRequest(
        List<ScheduleItemRequest> schedules
) {}
