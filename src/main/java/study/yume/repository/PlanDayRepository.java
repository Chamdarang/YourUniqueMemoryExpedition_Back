package study.yume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import study.yume.model.PlanDay;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanDayRepository extends JpaRepository<PlanDay, Long> {
    Boolean existsByUserIdAndPlanIdAndDayOrder(Long userId,Long planId, Integer dayOrder);
    Optional<PlanDay> findByUserIdAndId(Long userId, Long id);
    @EntityGraph(attributePaths = "plan")
    Optional<PlanDay> findOneByUserIdAndId(Long userId, Long id);
    Page<PlanDay> findAllByUserIdAndPlanIsNull(Long userId, Pageable pageable);
    List<PlanDay> findAllByUserIdAndPlanIdOrderByDayOrderAsc(Long userId,Long planId);

}
