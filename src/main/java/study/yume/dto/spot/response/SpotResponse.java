package study.yume.dto.spot.response;

import study.yume.model.Spot;
import study.yume.model.enums.SpotType;

import java.util.Map;

public record SpotResponse(
        Long id,
        String placeId,
        String spotName,
        SpotType spotType,
        String address,
        String shortAddress,
        String website,
        String googleMapUrl,
        Double lat, // Point의 y
        Double lng, // Point의 x
        Boolean isVisit,
        String description,
        Map<String ,Object> metadata

) {
    public static SpotResponse toDto(Spot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getPlaceId(),
                spot.getSpotName(),
                spot.getSpotType(),
                spot.getAddress(),
                spot.getShortAddress(),
                spot.getWebsite(),
                spot.getGoogleMapUrl(),
                spot.getLocation().getY(),
                spot.getLocation().getX(),
                spot.getIsVisit(),
                spot.getDescription(),
                spot.getMetadata()
        );
    }
}
