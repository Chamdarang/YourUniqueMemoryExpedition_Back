package study.yume.dto.schedule.request;

public record ScheduleUpdateMemoRequest(
        String memo,
        String movingMemo
) {
}
