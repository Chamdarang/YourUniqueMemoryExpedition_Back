package study.yume.dto.spot.response;

import study.yume.model.Spot;
import study.yume.model.SpotUser;
import study.yume.model.enums.SpotType;

import java.util.Map;

public record SpotResponse(
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
        Map<String ,Object> userMetadata // spotUser


) {
    public static SpotResponse toDto(Spot spot, SpotUser spotUser) {
        return new SpotResponse(
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
                spotUser.getMetadata()
        );
    }
}
