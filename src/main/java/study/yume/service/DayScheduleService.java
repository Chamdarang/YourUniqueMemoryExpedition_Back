package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.schedule.request.ScheduleCreateRequest;
import study.yume.dto.schedule.request.ScheduleReorderRequest;
import study.yume.dto.schedule.request.ScheduleUpdateRequest;
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
import java.time.LocalTime;
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

//    public List<DayScheduleResponse> syncSchedules(Long userId, Long dayId, ScheduleSyncRequest req) {
//        PlanDay planDay = findDayByUserIdAndId(userId, dayId);
//
//        dayScheduleRepository.deleteAllByUserIdAndPlanDayId(userId, dayId);
//
//        List<DaySchedule> newSchedules = req.schedules().stream()
//                .map(item->{
//                    DaySchedule s = new DaySchedule();
//                    Point point = geometryFactory.createPoint(new Coordinate(item.lng(),item.lat()));
//                    s.setUserId(userId);
//                    s.setPlanDay(planDay);
//                    s.setScheduleOrder(item.scheduleOrder());
//                    s.setSpotUser(spotUserRepository.findByUserIdAndId(userId, item.spotUserId()).orElse(null));
//                    s.setSpotNameSnapshot(item.spotName());
//                    s.setSpotLocationSnapshot(point);
//                    s.setSpotTypeSnapshot(item.spotType());
//                    s.setIsChecked(item.isChecked());
//                    s.setStartTime(item.startTime());
//                    s.setDuration(item.duration());
//                    s.setEndTime(item.endTime());
//                    s.setMovingDuration(item.movingDuration());
//                    s.setExtraDuration(item.extraDuration());
//                    s.setExtraMovingDuration(item.extraMovingDuration());
//                    s.setTransportation(item.transportation());
//                    s.setMemo(item.memo());
//                    s.setMovingMemo(item.movingMemo());
//
//                    return s;
//                })
//                .toList();
//
//        return dayScheduleRepository.saveAll(newSchedules).stream()
//                .map(DayScheduleResponse::toDto)
//                .toList();
//    }

    public List<DayScheduleResponse> createSchedule(Long userId, Long dayId, ScheduleCreateRequest req) {
        List<DaySchedule> schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, dayId);

        DaySchedule daySchedule = new DaySchedule();
        daySchedule.setUserId(userId);
        daySchedule.setPlanDay(findDayByUserIdAndId(userId, dayId));
        daySchedule.setScheduleOrder(req.scheduleOrder());
        daySchedule.setStartTime(LocalTime.of(9, 0));
        daySchedule.setDuration(60);

        schedules.add(req.scheduleOrder(), daySchedule);

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null,null, schedules);
        dayScheduleRepository.saveAll(updatedSchedules);
        return updatedSchedules.stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }

    public List<DayScheduleResponse> updateSchedule(Long userId, Long scheduleId, ScheduleUpdateRequest req) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);

        if (req.spotUserId() != null && req.spotUserId()!=0){
            SpotUser spotUser = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다."));
            schedule.setSpotUser(spotUser);
        }
        if (req.spotName() != null) schedule.setSpotNameSnapshot(req.spotName());
        if (req.lat() != null && req.lng() !=null) schedule.setSpotLocationSnapshot(geometryFactory.createPoint(new Coordinate(req.lng(),req.lat())));
        if (req.spotType() != null) schedule.setSpotTypeSnapshot(req.spotType());
        if (req.startTime() != null) schedule.setStartTime(req.startTime()); // 첫번쨰 일정일경우 이후 작업에서 업데이트 안되니 직접적용
        if (req.duration() != null) schedule.setDuration(req.duration());
        if (req.extraDuration() != null) schedule.setExtraDuration(req.extraDuration());
        if (req.movingDuration() != null) schedule.setMovingDuration(req.movingDuration());
        if (req.extraMovingDuration() != null) schedule.setExtraMovingDuration(req.extraMovingDuration());
        if (req.transportation() != null) schedule.setTransportation(req.transportation());
        if (req.memo() != null) schedule.setMemo(req.memo());
        if (req.movingMemo() != null) schedule.setMovingMemo(req.movingMemo());

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(userId, schedule.getPlanDay().getId(),null);
        dayScheduleRepository.saveAll(updatedSchedules);

        return updatedSchedules.stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }

    public List<DayScheduleResponse> reorderSchedule(Long userId, Long dayId, Long scheduleId, ScheduleReorderRequest req) {
        List<DaySchedule> schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, dayId);

        DaySchedule target = schedules.stream()
                .filter(s -> s.getId().equals(scheduleId))
                .findFirst()
                .orElseThrow();

        schedules.remove(target);
        schedules.add(req.scheduleOrder(), target); // 인덱스 기반 이동

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null, null,schedules);
        dayScheduleRepository.saveAll(updatedSchedules);

        return updatedSchedules.stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }

    public List<DayScheduleResponse> deleteSchedule(Long userId, Long scheduleId) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        dayScheduleRepository.delete(schedule);

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(userId, schedule.getPlanDay().getId(),null);
        dayScheduleRepository.saveAll(updatedSchedules);
        return updatedSchedules.stream()
                .map(DayScheduleResponse::toDto)
                .toList();
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

    private List<DaySchedule> recalculateTimesForDay(Long userId, Long dayId, List<DaySchedule> schedules) {
        if (schedules == null) schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, dayId);

        for (int i = 0; i < schedules.size(); i++) {
            DaySchedule current = schedules.get(i);
            current.setScheduleOrder(i);

            if (i == 0) {
                // 첫 일정의 시작 시간은 보존, 종료 시간만 갱신
                current.setEndTime(current.getStartTime().plusMinutes(current.getDuration() + current.getExtraDuration()));
            } else {
                DaySchedule prev = schedules.get(i - 1);
                LocalTime nextStart = prev.getEndTime().plusMinutes(current.getMovingDuration() + current.getExtraMovingDuration());

                current.setStartTime(nextStart);
                current.setEndTime(nextStart.plusMinutes(current.getDuration() + current.getExtraDuration()));
            }
        }
        return schedules;
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