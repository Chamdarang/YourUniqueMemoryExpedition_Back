package study.yume.dto.route.response;

import java.util.List;

public record PlanRouteAuditDayResponse(
        Long dayId,
        int dayOrder,
        String dayName,
        int scheduleCount,
        int issueCount,
        List<PlanScheduleAuditIssueResponse> scheduleIssues,
        DayRouteAuditResponse routeAudit
) {
}
