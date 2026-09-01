package study.yume.dto.route.response;

import java.util.List;

public record PlanRouteAuditResponse(
        boolean routesCalculated,
        int maxRouteCalculationLegs,
        int totalDays,
        int totalSchedules,
        int totalLegs,
        int issueCount,
        List<PlanRouteAuditDayResponse> days
) {
}
