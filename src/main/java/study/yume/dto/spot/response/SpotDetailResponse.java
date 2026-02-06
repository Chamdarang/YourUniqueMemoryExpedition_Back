package study.yume.dto.spot.response;

import study.yume.dto.spotpurchase.response.SpotPurchaseResponse;
import study.yume.dto.spotvisithistory.response.SpotVisitHistoryResponse;
import study.yume.model.Spot;
import study.yume.model.SpotGroup;
import study.yume.model.SpotUser;
import study.yume.model.enums.SpotType;

import java.util.List;
import java.util.Map;

public record SpotDetailResponse(
        Long id,                    //spotUser의 id
        Long spotId,                //spot의 id
        String placeId,             //spot
        String spotName,            //spot
        String displayName,         // spotUser
        SpotType spotType,          // spotUser
        String address,             //spot
        String shortAddress,        //spot
        String website,             //spot
        String googleMapUrl,        //spot
        Double lat,                 //spot, Point의 y
        Double lng,                 //spot, Point의 x
        Boolean isVisit,            // spotUser
        String description,         // spotUser
        Map<String ,Object> metadata, // spot
        Map<String ,Object> userMetadata, // spotUser
        List<String> groupName,     // sgm
        List<SpotPurchaseResponse> purchases,  //spotPurchase
        List<SpotVisitHistoryResponse> spotVisitHistory //spotVisitHistory
) {
    public static SpotDetailResponse toDto(Spot spot, SpotUser spotUser) {
        return new SpotDetailResponse(
                spotUser.getId(),
                spot.getId(),
                spot.getPlaceId(),
                spot.getSpotName(),
                spotUser.getCustomName(),
                spotUser.getSpotType(),
                spot.getAddress(),
                spot.getShortAddress(),
                spot.getWebsite(),
                spot.getGoogleMapUrl(),
                spot.getLocation().getY(),
                spot.getLocation().getX(),
                spotUser.getIsVisit(),
                spotUser.getDescription(),
                spot.getMetadata(),
                spotUser.getMetadata(),
                spotUser.getSpotGroup().stream()
                        .map(SpotGroup::getGroupName)
                        .toList(),
                spotUser.getSpotPurchases().stream()
                        .map(SpotPurchaseResponse::toDto)
                        .toList(),
                spotUser.getSpotVisitHistory().stream()
                        .map(SpotVisitHistoryResponse::toDto)
                        .toList()
        );
    }
}
