package study.yume.dto.planmember.response;

import study.yume.model.PlanMember;

import java.time.LocalDateTime;

public record PlanMemberResponse(
        Long id,
        Long userId,
        String username,
        String status,
        LocalDateTime invitedAt
) {
    public static PlanMemberResponse of(PlanMember member, String username) {
        return new PlanMemberResponse(
                member.getId(), member.getUserId(), username, member.getStatus().name(), member.getCreatedAt()
        );
    }
}
