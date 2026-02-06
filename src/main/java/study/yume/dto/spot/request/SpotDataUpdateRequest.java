package study.yume.dto.spot.request;

import study.yume.model.enums.SpotType;

import java.util.Map;

public record SpotDataUpdateRequest(
        String placeId,
        String spotName,
        SpotType spotType,
        String address,
        String shortAddress,
        String website,
        String googleMapUrl,
        Double lat,
        Double lng,
        Boolean isVisit,
        String description,
        Map<String, Object> metadata
) {
}
