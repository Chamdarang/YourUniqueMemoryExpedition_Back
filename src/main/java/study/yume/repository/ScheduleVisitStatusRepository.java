package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.ScheduleVisitStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduleVisitStatusRepository extends JpaRepository<ScheduleVisitStatus, Long> {
    List<ScheduleVisitStatus> findAllByUserIdAndScheduleIdIn(Long userId, Collection<Long> scheduleIds);

    Optional<ScheduleVisitStatus> findByUserIdAndScheduleId(Long userId, Long scheduleId);

    void deleteByUserIdAndScheduleId(Long userId, Long scheduleId);
}
