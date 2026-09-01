package study.yume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.model.Plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Page<Plan> findAllByUserId(Long userId, Pageable pageable);
    Optional<Plan> findByUserIdAndId(Long userId, Long id);

    @Query("SELECT p FROM Plan p WHERE p.userId = :userId " +
            "AND (:from IS NULL OR p.planEndDate >= :from) " +
            "AND (:to IS NULL OR p.planStartDate <= :to) " +
            "AND (COALESCE(:months, NULL) IS NULL OR MONTH(p.planStartDate) IN :months) " +
            "AND (:status = 'ALL' " +
            "OR (:status = 'UPCOMING' AND p.planStartDate > :today) " +
            "OR (:status = 'PAST' AND p.planEndDate < :today))")
    Page<Plan> findFilteredPlans(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("months") List<Integer> months,
            @Param("status") String status,
            @Param("today") LocalDate today,
            Pageable pageable);

    @Query("SELECT p FROM Plan p WHERE p.userId = :userId " +
            "AND p.planStartDate IS NOT NULL " +
            "AND p.planEndDate IS NOT NULL " +
            "AND p.planEndDate >= :today " +
            "ORDER BY p.planStartDate ASC, p.id ASC") // 가장 먼저 시작한 것(진행 중인 것 포함) 우선
    List<Plan> findUpcomingOrCurrentPlan(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable pageable);

}
