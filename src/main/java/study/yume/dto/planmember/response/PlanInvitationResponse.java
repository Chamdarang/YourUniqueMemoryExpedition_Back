package study.yume.dto.planmember.response;

import java.time.LocalDateTime;

public record PlanInvitationResponse(
        Long id,
        Long planId,
        String planName,
        String inviterUsername,
        LocalDateTime invitedAt
) {
}
