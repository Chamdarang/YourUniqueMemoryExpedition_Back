package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import study.yume.dto.ApiResponse;
import study.yume.dto.planmember.request.InvitePlanMemberRequest;
import study.yume.dto.planmember.response.PlanInvitationResponse;
import study.yume.dto.planmember.response.PlanMemberResponse;
import study.yume.dto.planmember.response.PlanCollaboratorResponse;
import study.yume.dto.planmember.response.SharedPlanResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanMemberService;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanMemberController {
    private final PlanMemberService planMemberService;

    @GetMapping("/shared")
    public ResponseEntity<ApiResponse<List<SharedPlanResponse>>> getSharedPlans(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(planMemberService.getSharedPlans(user.getId())));
    }

    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<List<PlanInvitationResponse>>> getInvitations(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(planMemberService.getInvitations(user.getId())));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long invitationId
    ) {
        planMemberService.accept(user.getId(), invitationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> declineInvitation(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long invitationId
    ) {
        planMemberService.decline(user.getId(), invitationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{planId}/members")
    public ResponseEntity<ApiResponse<List<PlanMemberResponse>>> getMembers(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok(ApiResponse.success(planMemberService.getMembers(user.getId(), planId)));
    }

    @GetMapping("/{planId}/collaborators")
    public ResponseEntity<ApiResponse<List<PlanCollaboratorResponse>>> getCollaborators(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok(ApiResponse.success(planMemberService.getCollaborators(user.getId(), planId)));
    }

    @PostMapping("/{planId}/members")
    public ResponseEntity<ApiResponse<PlanMemberResponse>> inviteMember(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @RequestBody InvitePlanMemberRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(planMemberService.invite(user.getId(), planId, request)));
    }

    @DeleteMapping("/{planId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @PathVariable Long memberId
    ) {
        planMemberService.removeMember(user.getId(), planId, memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
