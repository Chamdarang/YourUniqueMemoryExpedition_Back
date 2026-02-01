package study.yume.dto.spot.response;

import study.yume.dto.spotpurchase.response.SpotPurchaseResponse;
import study.yume.model.Spot;
import study.yume.model.SpotGroup;
import study.yume.model.enums.SpotType;

import java.util.List;
import java.util.Map;

public record SpotDetailResponse(
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
        Map<String ,Object> metadata,
        List<String> groupName,
        List<SpotPurchaseResponse> purchases
) {
    public static SpotDetailResponse toDto(Spot spot) {
        return new SpotDetailResponse(
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
                spot.getMetadata(),
                spot.getSpotGroup().stream()
                        .map(SpotGroup::getGroupName)
                        .toList(),
                spot.getSpotPurchases().stream()
                        .map(SpotPurchaseResponse::toDto)
                        .toList()
        );
    }
}
