package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.DaySchedule;
import study.yume.model.SpotUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface DayScheduleRepository extends JpaRepository<DaySchedule, Long> {
    Optional<DaySchedule> findByUserIdAndId(Long userId, Long id);
    List<DaySchedule> findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(Long userId, Long dayId);
    List<DaySchedule> findAllByUserIdAndPlanDayPlanIdOrderByPlanDayDayOrderAscScheduleOrderAsc(Long userId, Long planId);
    List<DaySchedule> findAllByUserIdAndPlanDayPlanIdAndSpotUserIsNullOrderByPlanDayDayOrderAscScheduleOrderAsc(Long userId, Long planId);
    void deleteAllByUserIdAndPlanDayId(Long userId, Long dayId);

    @Query("SELECT " +
            "ds.id as scheduleId, " +
            "p.id as planId, " +
            "pd.id as dayId, " +
            "p.planName as planName, " +
            "pd.dayName as dayName, " +
            "ds.scheduleOrder as scheduleOrder " +
            "FROM DaySchedule ds " +
            "JOIN ds.planDay pd " +
            "LEFT JOIN pd.plan p " +
            "WHERE ds.userId = :userId AND ds.spotUser.id = :spotUserId")
    List<UsedScheduleProjection> findUsageBySpotId(@Param("userId") Long userId, @Param("spotUserId") Long spotUserId);

    @Modifying
    @Query("UPDATE DaySchedule ds SET ds.spotUser = :target " +
            "WHERE ds.userId = :userId AND ds.spotUser.id = :sourceId")
    int reassignSpotUser(
            @Param("userId") Long userId,
            @Param("sourceId") Long sourceId,
            @Param("target") SpotUser target
    );
}
