package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.SpotVisitHistory;

public interface SpotVisitHistoryRepository extends JpaRepository<SpotVisitHistory, Long> {
    void deleteByUserIdAndSpotUserIdAndDayId(Long userId, Long spotUserId, Long dayId);
}
