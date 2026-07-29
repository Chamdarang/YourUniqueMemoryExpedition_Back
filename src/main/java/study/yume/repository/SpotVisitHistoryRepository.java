package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.yume.model.SpotVisitHistory;
import study.yume.model.SpotUser;

public interface SpotVisitHistoryRepository extends JpaRepository<SpotVisitHistory, Long> {
    void deleteByUserIdAndSpotUserIdAndDayId(Long userId, Long spotUserId, Long dayId);

    @Modifying
    @Query("UPDATE SpotVisitHistory history SET history.spotUser = :target " +
            "WHERE history.userId = :userId AND history.spotUser.id = :sourceId")
    int reassignSpotUser(
            @Param("userId") Long userId,
            @Param("sourceId") Long sourceId,
            @Param("target") SpotUser target
    );
}
