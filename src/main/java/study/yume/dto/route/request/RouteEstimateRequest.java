package study.yume.dto.route.request;

import study.yume.model.enums.Transportation;

public record RouteEstimateRequest(
        double originLat,
        double originLng,
        double destinationLat,
        double destinationLng,
        Transportation transportation,
        String departureTime
) {
}
