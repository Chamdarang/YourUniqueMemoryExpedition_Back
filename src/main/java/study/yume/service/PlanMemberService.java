package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.planmember.request.InvitePlanMemberRequest;
import study.yume.dto.planmember.response.PlanInvitationResponse;
import study.yume.dto.planmember.response.PlanMemberResponse;
import study.yume.dto.planmember.response.PlanCollaboratorResponse;
import study.yume.dto.planmember.response.SharedPlanResponse;
import study.yume.model.Plan;
import study.yume.model.PlanMember;
import study.yume.model.PlanMemberStatus;
import study.yume.model.User;
import study.yume.repository.PlanMemberRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanShareRepository;
import study.yume.repository.UserRepository;

import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanMemberService {
    private final PlanAccessService planAccessService;
    private final PlanMemberRepository planMemberRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlanShareRepository planShareRepository;
    private final PlanLiveUpdateService planLiveUpdateService;

    public PlanMemberResponse invite(Long ownerId, Long planId, InvitePlanMemberRequest request) {
        Plan plan = planAccessService.requireOwner(ownerId, planId);
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("초대할 계정명을 입력해 주세요.");
        }
        User invitee = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new EntityNotFoundException("해당 계정을 찾을 수 없습니다."));
        if (invitee.getId().equals(ownerId)) {
            throw new IllegalArgumentException("계획 소유자는 초대할 수 없습니다.");
        }
        if (planMemberRepository.findByPlanIdAndUserId(planId, invitee.getId()).isPresent()) {
            throw new IllegalArgumentException("이미 초대했거나 함께 편집 중인 계정입니다.");
        }

        PlanMember member = new PlanMember();
        member.setPlanId(plan.getId());
        member.setUserId(invitee.getId());
        member.setInvitedByUserId(ownerId);
        member.setStatus(PlanMemberStatus.PENDING);
        return PlanMemberResponse.of(planMemberRepository.save(member), invitee.getUsername());
    }

    @Transactional(readOnly = true)
    public List<PlanMemberResponse> getMembers(Long ownerId, Long planId) {
        planAccessService.requireOwner(ownerId, planId);
        return planMemberRepository.findAllByPlanIdOrderByCreatedAtAsc(planId).stream()
                .filter(this::hasActiveShareGrant)
                .map(member -> PlanMemberResponse.of(
                        member,
                        userRepository.findById(member.getUserId()).map(User::getUsername).orElse("탈퇴한 사용자")
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanCollaboratorResponse> getCollaborators(Long userId, Long planId) {
        Plan plan = planAccessService.requireEditor(userId, planId);
        List<PlanCollaboratorResponse> collaborators = new java.util.ArrayList<>();
        userRepository.findById(plan.getUserId()).ifPresent(owner -> collaborators.add(
                new PlanCollaboratorResponse(owner.getId(), displayName(owner), "OWNER")
        ));
        planMemberRepository.findAllByPlanIdOrderByCreatedAtAsc(planId).stream()
                .filter(member -> member.getStatus() == PlanMemberStatus.ACCEPTED)
                .filter(this::hasActiveShareGrant)
                .map(member -> userRepository.findById(member.getUserId())
                        .map(editor -> new PlanCollaboratorResponse(editor.getId(), displayName(editor), "EDITOR"))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .forEach(collaborators::add);
        return List.copyOf(collaborators);
    }

    private String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    @Transactional(readOnly = true)
    public List<PlanInvitationResponse> getInvitations(Long userId) {
        return planMemberRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanMemberStatus.PENDING)
                .stream()
                .map(member -> {
                    Plan plan = planRepository.findById(member.getPlanId())
                            .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
                    String inviterName = userRepository.findById(member.getInvitedByUserId())
                            .map(User::getUsername).orElse("탈퇴한 사용자");
                    return new PlanInvitationResponse(
                            member.getId(), plan.getId(), plan.getPlanName(), inviterName, member.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedPlanResponse> getSharedPlans(Long userId) {
        return planMemberRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanMemberStatus.ACCEPTED).stream()
                .filter(this::hasActiveShareGrant)
                .map(member -> {
                    Plan plan = planRepository.findById(member.getPlanId())
                            .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
                    String ownerName = userRepository.findById(plan.getUserId())
                            .map(owner -> owner.getNickname() == null || owner.getNickname().isBlank()
                                    ? owner.getUsername() : owner.getNickname())
                            .orElse("탈퇴한 사용자");
                    return new SharedPlanResponse(
                            plan.getId(), plan.getPlanName(), plan.getPlanStartDate(), plan.getPlanEndDate(),
                            plan.getPlanDays(), plan.getPlanMemo(), ownerName
                    );
                })
                .toList();
    }

    private boolean hasActiveShareGrant(PlanMember member) {
        if (member.getShareId() == null) return true;
        return planShareRepository.findById(member.getShareId())
                .filter(share -> share.getRevokedAt() == null)
                .filter(share -> share.getExpiresAt() == null || share.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    public void accept(Long userId, Long invitationId) {
        PlanMember member = getPendingInvitation(userId, invitationId);
        member.setStatus(PlanMemberStatus.ACCEPTED);
        planMemberRepository.save(member);
        planLiveUpdateService.publishAfterCommit(member.getPlanId(), userId);
    }

    public void decline(Long userId, Long invitationId) {
        planMemberRepository.delete(getPendingInvitation(userId, invitationId));
    }

    public void removeMember(Long ownerId, Long planId, Long memberId) {
        planAccessService.requireOwner(ownerId, planId);
        PlanMember member = planMemberRepository.findById(memberId)
                .filter(candidate -> candidate.getPlanId().equals(planId))
                .orElseThrow(() -> new EntityNotFoundException("초대 또는 멤버를 찾을 수 없습니다."));
        planMemberRepository.delete(member);
        planLiveUpdateService.closeUserAfterCommit(planId, member.getUserId());
        planLiveUpdateService.publishAfterCommit(planId, ownerId);
    }

    private PlanMember getPendingInvitation(Long userId, Long invitationId) {
        return planMemberRepository.findByIdAndUserIdAndStatus(
                        invitationId, userId, PlanMemberStatus.PENDING
                )
                .orElseThrow(() -> new EntityNotFoundException("대기 중인 초대를 찾을 수 없습니다."));
    }
}
