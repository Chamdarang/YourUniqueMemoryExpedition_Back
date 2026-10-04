package study.yume.dto.planshare.response;

import java.time.LocalDateTime;

public record PlanShareLinkResponse(
        String token,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
