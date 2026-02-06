package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.schedule.request.ScheduleSyncRequest;
import study.yume.dto.schedule.request.ScheduleUpdateMemoRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.SpotUser;
import study.yume.model.SpotVisitHistory;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DayScheduleService {

    private final DayScheduleRepository dayScheduleRepository;
    private final PlanDayRepository plandayRepository;
    private final SpotUserRepository spotUserRepository;
    private final SpotVisitHistoryRepository spotVisitHistoryRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional(readOnly = true)
    public List<DayScheduleResponse> getSchedulesByDayId(Long userId, Long dayId) {
        findDayByUserIdAndId(userId, dayId);

        return dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId,dayId).stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }
    public void deleteSchedule(Long userId, Long scheduleId) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        dayScheduleRepository.delete(schedule);
    }

    public List<DayScheduleResponse> syncSchedules(Long userId, Long dayId, ScheduleSyncRequest req) {
        PlanDay planDay = findDayByUserIdAndId(userId, dayId);

        dayScheduleRepository.deleteAllByUserIdAndPlanDayId(userId, dayId);

        List<DaySchedule> newSchedules = req.schedules().stream()
                .map(item->{
                    DaySchedule s = new DaySchedule();
                    Point point = geometryFactory.createPoint(new Coordinate(item.lng(),item.lat()));
                    s.setUserId(userId);
                    s.setPlanDay(planDay);
                    s.setScheduleOrder(item.scheduleOrder());
                    s.setSpotUser(spotUserRepository.findByUserIdAndId(userId, item.spotUserId()).orElse(null));
                    s.setSpotNameSnapshot(item.spotName());
                    s.setSpotLocationSnapshot(point);
                    s.setSpotTypeSnapshot(item.spotType());
                    s.setIsChecked(item.isChecked());
                    s.setStartTime(item.startTime());
                    s.setDuration(item.duration());
                    s.setEndTime(item.endTime());
                    s.setMovingDuration(item.movingDuration());
                    s.setTransportation(item.transportation());
                    s.setMemo(item.memo());
                    s.setMovingMemo(item.movingMemo());

                    return s;
                })
                .toList();

        return dayScheduleRepository.saveAll(newSchedules).stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }

    public DayScheduleResponse updateMemo(Long userId, Long scheduleId, ScheduleUpdateMemoRequest req) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        schedule.setMemo(req.memo());
        schedule.setMovingMemo(req.movingMemo());
        return DayScheduleResponse.toDto(dayScheduleRepository.save(schedule));
    }

    public void updateVisit(Long userId, Long scheduleId){
        DaySchedule schedule = findScheduleByUserIdAndId(userId,scheduleId);
        boolean newCheckedState = !schedule.getIsChecked();
        schedule.setIsChecked(newCheckedState);

        if (schedule.getSpotUser()!=null ){
            SpotUser spotUser=schedule.getSpotUser();

            if (newCheckedState){
                spotUser.setIsVisit(true);

                SpotVisitHistory spotVisitHistory = new SpotVisitHistory();
                spotVisitHistory.setUserId(userId);
                spotVisitHistory.setSpotUser(spotUser);
                spotVisitHistory.setDayId(schedule.getPlanDay().getId());
                spotVisitHistory.setDayNameSnapshot(schedule.getPlanDay().getDayName());
                spotVisitHistory.setVisitedAt(LocalDate.now());

                if (schedule.getPlanDay().getPlan() != null) {
                    spotVisitHistory.setPlanId(schedule.getPlanDay().getPlan().getId());
                    spotVisitHistory.setPlanNameSnapshot(schedule.getPlanDay().getPlan().getPlanName());
                }

                spotVisitHistoryRepository.save(spotVisitHistory);
            }else{
                spotVisitHistoryRepository.deleteByUserIdAndSpotUserIdAndDayId(
                        userId, spotUser.getId(), schedule.getPlanDay().getId()
                );

                // 이 장소를 방문한 기록이 전무하게 되었을 경우에도 일단 '이 장소에 가본적은 있음'을 유지
            }
        }
    }


    public List<UsedScheduleProjection> findUsageBySpotId(Long userId, Long spotUserId) {
        return dayScheduleRepository.findUsageBySpotId(userId, spotUserId);
    }


    private DaySchedule findScheduleByUserIdAndId(Long userId, Long planId) {
        return dayScheduleRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
    }

    private PlanDay findDayByUserIdAndId(Long userId, Long planId) {
        return plandayRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("해당 하루 일정을 찾을 수 없습니다."));
    }



}