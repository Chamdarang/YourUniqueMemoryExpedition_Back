package study.yume.dto.route.request;

import java.util.List;

public record DayRouteApplyRequest(List<RouteDuration> routes) {
    public record RouteDuration(Long scheduleId, Integer estimatedDurationMinutes) {
    }
}
