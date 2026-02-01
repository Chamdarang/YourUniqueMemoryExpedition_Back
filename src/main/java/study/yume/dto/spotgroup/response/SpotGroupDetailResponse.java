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
                group.getSpots() != null ? group.getSpots().size() : 0,
                group.getSpots() != null ? group.getSpots().stream()
                        .map(SpotResponse::toDto)
                        .toList() : List.of()
        );
    }
}
