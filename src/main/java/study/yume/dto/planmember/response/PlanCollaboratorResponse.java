package study.yume.dto.planmember.response;

public record PlanCollaboratorResponse(
        Long userId,
        String nickname,
        String role
) {
}
