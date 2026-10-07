package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import study.yume.model.PlanMember;
import study.yume.model.PlanMemberStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanMemberRepository extends JpaRepository<PlanMember, Long> {
    Optional<PlanMember> findByPlanIdAndUserId(Long planId, Long userId);

    Optional<PlanMember> findByPlanIdAndUserIdAndStatus(Long planId, Long userId, PlanMemberStatus status);

    Optional<PlanMember> findByIdAndUserIdAndStatus(Long id, Long userId, PlanMemberStatus status);

    List<PlanMember> findAllByPlanIdOrderByCreatedAtAsc(Long planId);

    List<PlanMember> findAllByShareId(Long shareId);

    Optional<PlanMember> findByShareIdAndUserId(Long shareId, Long userId);

    List<PlanMember> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PlanMemberStatus status);
}
