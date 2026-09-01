package study.yume.dto.route.response;

import java.util.List;

public record DayRouteAuditResponse(
        int totalLegs,
        int calculatedLegs,
        int issueCount,
        int plannedTotalMinutes,
        int estimatedTotalMinutes,
        List<DayRouteAuditLegResponse> legs
) {
}
