package study.yume.dto.route.response;

public record RouteEstimateResponse(
        int durationMinutes,
        int distanceMeters,
        String encodedPolyline,
        String movingMemo
) {
}
