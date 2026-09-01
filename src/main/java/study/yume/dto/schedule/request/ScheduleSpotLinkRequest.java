package study.yume.dto.schedule.request;

public record ScheduleSpotLinkRequest(
        String sourceSpotName,
        Long spotUserId
) {
}
