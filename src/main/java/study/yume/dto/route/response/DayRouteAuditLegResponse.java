package study.yume.dto.route.response;

import study.yume.model.enums.Transportation;

import java.time.LocalTime;

public record DayRouteAuditLegResponse(
        Long fromScheduleId,
        String fromSpotName,
        Long toScheduleId,
        String toSpotName,
        Transportation transportation,
        int plannedDurationMinutes,
        Integer estimatedDurationMinutes,
        String encodedPolyline,
        Integer differenceMinutes,
        LocalTime estimatedArrivalTime,
        Integer fixedStartConflictMinutes,
        String status,
        String message
) {
}
