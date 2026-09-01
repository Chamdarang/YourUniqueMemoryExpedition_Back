package study.yume.dto.route.response;

public record PlanScheduleAuditIssueResponse(
        Long scheduleId,
        String spotName,
        String severity,
        String code,
        String message
) {
}
