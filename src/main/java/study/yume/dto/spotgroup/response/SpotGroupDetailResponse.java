package study.yume.dto.spotgroup.response;

import study.yume.dto.spot.response.SpotResponse;
import study.yume.model.SpotGroup;

import java.util.List;

public record SpotGroupDetailResponse(
        Long id,
        String groupName,
        int spotCount,
        List<SpotResponse> spots
) {
    public static SpotGroupDetailResponse toDto(SpotGroup group) {
        return new SpotGroupDetailResponse(
                group.getId(),
                group.getGroupName(),
                group.getSpotUsers() != null ? group.getSpotUsers().size() : 0,
                group.getSpotUsers() != null ? group.getSpotUsers().stream()
                        .map(spotUser -> SpotResponse.toDto(spotUser.getSpot(),spotUser))
                        .toList() : List.of()
        );
    }
}
