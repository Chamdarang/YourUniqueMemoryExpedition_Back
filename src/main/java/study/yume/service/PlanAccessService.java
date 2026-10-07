package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.model.Plan;
import study.yume.model.PlanMemberStatus;
import study.yume.model.PlanMember;
import study.yume.model.PlanShare;
import study.yume.repository.PlanMemberRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanShareRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanAccessService {
    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlanShareRepository planShareRepository;

    public Plan requireEditor(Long userId, Long planId) {
        Plan plan = findPlan(planId);
        if (plan.getUserId().equals(userId)) return plan;
        PlanMember member = planMemberRepository.findByPlanIdAndUserIdAndStatus(
                planId, userId, PlanMemberStatus.ACCEPTED
        ).orElse(null);
        boolean accepted = member != null && hasActiveGrant(member);
        if (!accepted) throw notFound();
        return plan;
    }

    private boolean hasActiveGrant(PlanMember member) {
        if (member.getShareId() == null) return true;
        return planShareRepository.findById(member.getShareId())
                .filter(share -> share.getRevokedAt() == null)
                .filter(share -> share.getExpiresAt() == null || share.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    public Plan requireOwner(Long userId, Long planId) {
        Plan plan = findPlan(planId);
        if (!plan.getUserId().equals(userId)) throw notFound();
        return plan;
    }

    public Long requireEditorOwnerId(Long userId, Long planId) {
        return requireEditor(userId, planId).getUserId();
    }

    public Long requireOwnerId(Long userId, Long planId) {
        return requireOwner(userId, planId).getUserId();
    }

    public boolean isOwner(Long userId, Long planId) {
        return findPlan(planId).getUserId().equals(userId);
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId).orElseThrow(PlanAccessService::notFound);
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("여행 계획을 찾을 수 없습니다.");
    }
}
