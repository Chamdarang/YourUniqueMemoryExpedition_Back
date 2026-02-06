package study.yume.dto.spot.request;

import java.util.Map;

public record SpotUpdateRequest(
        String customName,
        Boolean isVisit,
        String description,
        Map<String, Object> metadata
) {
}

