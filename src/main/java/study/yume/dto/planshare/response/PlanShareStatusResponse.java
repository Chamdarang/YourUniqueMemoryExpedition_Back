package study.yume.dto.planshare.response;

import java.time.LocalDateTime;

public record PlanShareStatusResponse(
        String state,
        boolean active,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt,
        String token,
        String accessMode
) {
}
