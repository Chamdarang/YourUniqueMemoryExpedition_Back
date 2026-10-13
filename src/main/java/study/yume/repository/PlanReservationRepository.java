package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.PlanReservation;

import java.util.List;
import java.util.Optional;

public interface PlanReservationRepository extends JpaRepository<PlanReservation, Long> {
    List<PlanReservation> findAllByPlanIdOrderByStartDateAscStartTimeAscIdAsc(Long planId);
    Optional<PlanReservation> findByIdAndPlanId(Long id, Long planId);
}
