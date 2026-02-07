package study.yume.dto.spot.request;

import study.yume.model.enums.SpotType;

import java.util.Map;

public record SpotUpdateRequest(
        String customName,
        SpotType spotType,
        Boolean isVisit,
        String description,
        Map<String, Object> metadata
) {
}

