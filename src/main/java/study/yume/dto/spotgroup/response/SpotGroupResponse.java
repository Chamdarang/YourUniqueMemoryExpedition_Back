package study.yume.dto.spotgroup.response;

import study.yume.model.SpotGroup;

public record SpotGroupResponse(
        Long id,
        String groupName,
        int spotCount
) {
    public static SpotGroupResponse toDto(SpotGroup group) {
        return new SpotGroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getSpotUsers()!=null?group.getSpotUsers().size():0
        );
    }
}
