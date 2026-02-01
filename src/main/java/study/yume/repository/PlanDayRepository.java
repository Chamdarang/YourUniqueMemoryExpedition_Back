package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.PlanDay;

import java.util.List;
import java.util.Optional;

public interface PlanDayRepository extends JpaRepository<PlanDay, Long> {
    Boolean existsByUserIdAndPlanIdAndDayOrder(Long userId,Long planId, Integer dayOrder);
    Optional<PlanDay> findByUserIdAndId(Long userId, Long id);
    List<PlanDay> findAllByUserIdAndPlanIsNull(Long userId);
    List<PlanDay> findAllByUserIdAndPlanIdOrderByDayOrderAsc(Long userId,Long planId);

}
