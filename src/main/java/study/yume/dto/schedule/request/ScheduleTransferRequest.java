package study.yume.dto.schedule.request;

public record ScheduleTransferRequest(
        Long targetDayId,
        Integer targetOrder,
        boolean copy
) {
}
