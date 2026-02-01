package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.schedule.request.ScheduleSyncRequest;
import study.yume.dto.schedule.request.ScheduleUpdateMemoRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DayScheduleService {

    private final DayScheduleRepository dayScheduleRepository;
    private final PlanDayRepository plandayRepository;
    private final SpotRepository spotRepository;

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
                    s.setUserId(userId);
                    s.setPlanDay(planDay);
                    s.setSpot(spotRepository.findByUserIdAndId(userId, item.spotId()).orElse(null));
                    s.setScheduleOrder(item.scheduleOrder());
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
        return DayScheduleResponse.toDto(dayScheduleRepository.save(schedule));
    }


    public List<UsedScheduleProjection> findUsageBySpotId(Long userId, Long spotId) {
        return dayScheduleRepository.findUsageBySpotId(userId, spotId);
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