package study.yume.dto.spot.request;

import study.yume.model.enums.SpotType;

public record SpotGetFilteredRequest(
        Double lat,
        Double lng,
        Double radius,
        Boolean isVisit,
        SpotType spotType
) {
}
