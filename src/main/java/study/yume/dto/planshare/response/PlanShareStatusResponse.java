package study.yume.dto.planshare.response;

import java.time.LocalDateTime;

public record PlanShareStatusResponse(
        boolean active,
        LocalDateTime updatedAt
) {
}
