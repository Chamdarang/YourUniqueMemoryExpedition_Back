package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.PlanShare;

import java.util.Optional;

public interface PlanShareRepository extends JpaRepository<PlanShare, Long> {
    Optional<PlanShare> findByPlanId(Long planId);
    Optional<PlanShare> findByTokenHash(String tokenHash);
}
