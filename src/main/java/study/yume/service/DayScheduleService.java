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
import study.yume.dto.schedule.request.ScheduleSpotLinkRequest;
import study.yume.dto.schedule.request.ScheduleTransferRequest;
import study.yume.dto.route.request.DayRouteApplyRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.dto.schedule.response.UnlinkedSpotGroupResponse;
import study.yume.dto.schedule.response.ScheduleTransferResponse;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.SpotUser;
import study.yume.model.SpotVisitHistory;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;
import study.yume.repository.PlanRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DayScheduleService {

    private final DayScheduleRepository dayScheduleRepository;
    private final PlanDayRepository plandayRepository;
    private final SpotUserRepository spotUserRepository;
    private final SpotVisitHistoryRepository spotVisitHistoryRepository;
    private final PlanRepository planRepository;
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

        if (req.scheduleOrder() < 0 || req.scheduleOrder() > schedules.size()) {
            throw new IllegalArgumentException("일정을 추가할 위치가 올바르지 않습니다.");
        }

        PlanDay planDay = findDayByUserIdAndId(userId, dayId);
        DaySchedule daySchedule = new DaySchedule();
        daySchedule.setUserId(userId);
        daySchedule.setPlanDay(planDay);
        daySchedule.setScheduleOrder(req.scheduleOrder());
        daySchedule.setStartTime(req.startTime() != null
                ? req.startTime()
                : planDay.getScheduleMode() == ScheduleMode.SIMPLE ? null : LocalTime.of(9, 0));
        daySchedule.setFixedStartTime(req.startTime() != null);
        daySchedule.setDuration(60);
        daySchedule.setMemo(req.memo());

        if (req.spotUserId() != null) {
            SpotUser spotUser = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                    .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다."));
            daySchedule.setSpotUser(spotUser);
            daySchedule.setSpotNameSnapshot(
                    spotUser.getCustomName() == null || spotUser.getCustomName().isBlank()
                            ? spotUser.getSpot().getSpotName()
                            : spotUser.getCustomName()
            );
            daySchedule.setSpotLocationSnapshot(spotUser.getSpot().getLocation());
            daySchedule.setSpotTypeSnapshot(spotUser.getSpotType());
        } else if (req.spotName() != null && !req.spotName().isBlank()) {
            daySchedule.setSpotNameSnapshot(req.spotName().trim());
            if (req.lat() != null && req.lng() != null) {
                daySchedule.setSpotLocationSnapshot(
                        geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()))
                );
                daySchedule.setSpotTypeSnapshot(req.spotType());
            }
        }

        schedules.add(req.scheduleOrder(), daySchedule);
        if (planDay.getScheduleMode() == ScheduleMode.SIMPLE) adjustSimpleDurations(schedules);

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null,null, schedules);
        dayScheduleRepository.saveAll(updatedSchedules);
        return updatedSchedules.stream()
                .map(DayScheduleResponse::toDto)
                .toList();
    }

    public List<DayScheduleResponse> updateSchedule(Long userId, Long scheduleId, ScheduleUpdateRequest req) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);

        if (req.spotUserId() != null) {
            if (req.spotUserId() == 0) {
                schedule.setSpotUser(null);
            } else {
                SpotUser spotUser = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                    .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다."));
                schedule.setSpotUser(spotUser);
            }
        }
        if (req.spotName() != null) schedule.setSpotNameSnapshot(req.spotName());
        if (req.lat() != null && req.lng() !=null) schedule.setSpotLocationSnapshot(geometryFactory.createPoint(new Coordinate(req.lng(),req.lat())));
        if (req.spotType() != null) schedule.setSpotTypeSnapshot(req.spotType());
        if (req.startTime() != null) schedule.setStartTime(req.startTime()); // 첫번쨰 일정일경우 이후 작업에서 업데이트 안되니 직접적용
        if (req.fixedStartTime() != null) schedule.setFixedStartTime(req.fixedStartTime());
        if (req.duration() != null) schedule.setDuration(req.duration());
        if (req.extraDuration() != null) schedule.setExtraDuration(req.extraDuration());
        if (req.movingDuration() != null) schedule.setMovingDuration(req.movingDuration());
        if (req.extraMovingDuration() != null) schedule.setExtraMovingDuration(req.extraMovingDuration());
        if (req.transportation() != null) schedule.setTransportation(req.transportation());
        if (req.memo() != null) schedule.setMemo(req.memo());
        if (req.movingMemo() != null) schedule.setMovingMemo(req.movingMemo());

        List<DaySchedule> schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(
                userId,
                schedule.getPlanDay().getId()
        );
        if (schedule.getPlanDay().getScheduleMode() == ScheduleMode.SIMPLE) adjustSimpleDurations(schedules);
        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null, null, schedules);
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

    public ScheduleTransferResponse transferSchedule(Long userId, Long scheduleId, ScheduleTransferRequest req) {
        if (req == null || req.targetDayId() == null) {
            throw new IllegalArgumentException("대상 일차를 선택해 주세요.");
        }

        DaySchedule source = findScheduleByUserIdAndId(userId, scheduleId);
        PlanDay sourceDay = source.getPlanDay();
        PlanDay targetDay = findDayByUserIdAndId(userId, req.targetDayId());
        if (sourceDay.getId().equals(targetDay.getId())) {
            throw new IllegalArgumentException("현재 일차가 아닌 다른 일차를 선택해 주세요.");
        }

        List<DaySchedule> sourceSchedules = new java.util.ArrayList<>(
                dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, sourceDay.getId())
        );
        List<DaySchedule> targetSchedules = new java.util.ArrayList<>(
                dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, targetDay.getId())
        );
        int targetOrder = req.targetOrder() == null ? targetSchedules.size() : req.targetOrder();
        if (targetOrder < 0 || targetOrder > targetSchedules.size()) {
            throw new IllegalArgumentException("일정을 옮길 위치가 올바르지 않습니다.");
        }

        DaySchedule transferred;
        if (req.copy()) {
            transferred = copySchedule(source, targetDay);
        } else {
            if (!sourceSchedules.remove(source)) throw new EntityNotFoundException("원본 일정을 찾을 수 없습니다.");
            transferred = source;
            transferred.setPlanDay(targetDay);
        }
        targetSchedules.add(targetOrder, transferred);

        if (targetDay.getScheduleMode() == ScheduleMode.DETAILED
                && targetOrder == 0
                && transferred.getStartTime() == null) {
            transferred.setStartTime(LocalTime.of(9, 0));
        }
        if (!req.copy()) {
            if (sourceDay.getScheduleMode() == ScheduleMode.SIMPLE) adjustSimpleDurations(sourceSchedules);
            sourceSchedules = recalculateTimesForDay(null, null, sourceSchedules);
            dayScheduleRepository.saveAll(sourceSchedules);
        }
        if (targetDay.getScheduleMode() == ScheduleMode.SIMPLE) adjustSimpleDurations(targetSchedules);
        targetSchedules = recalculateTimesForDay(null, null, targetSchedules);
        dayScheduleRepository.saveAll(targetSchedules);

        return new ScheduleTransferResponse(
                transferred.getId(),
                sourceDay.getId(),
                targetDay.getId(),
                sourceSchedules.stream().map(DayScheduleResponse::toDto).toList(),
                targetSchedules.stream().map(DayScheduleResponse::toDto).toList()
        );
    }

    private DaySchedule copySchedule(DaySchedule source, PlanDay targetDay) {
        DaySchedule copy = new DaySchedule();
        copy.setUserId(source.getUserId());
        copy.setPlanDay(targetDay);
        copy.setSpotUser(source.getSpotUser());
        copy.setSpotNameSnapshot(source.getSpotNameSnapshot());
        copy.setSpotLocationSnapshot(source.getSpotLocationSnapshot() == null
                ? null
                : (org.locationtech.jts.geom.Point) source.getSpotLocationSnapshot().copy());
        copy.setSpotTypeSnapshot(source.getSpotTypeSnapshot());
        copy.setIsChecked(false);
        copy.setIsSkipped(false);
        copy.setStartTime(source.getStartTime());
        copy.setFixedStartTime(source.isFixedStartTime());
        copy.setDuration(source.getDuration());
        copy.setEndTime(source.getEndTime());
        copy.setMovingDuration(source.getMovingDuration());
        copy.setExtraDuration(source.getExtraDuration());
        copy.setExtraMovingDuration(source.getExtraMovingDuration());
        copy.setTransportation(source.getTransportation());
        copy.setMemo(source.getMemo());
        copy.setMovingMemo(source.getMovingMemo());
        return copy;
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


    @Transactional
    public void updateVisit(Long userId, Long scheduleId){
        DaySchedule schedule = findScheduleByUserIdAndId(userId,scheduleId);
        boolean newCheckedState = !schedule.getIsChecked();
        schedule.setIsChecked(newCheckedState);
        if (newCheckedState) schedule.setIsSkipped(false);

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

        boolean simpleMode = schedules.stream().anyMatch(schedule ->
                schedule.getPlanDay() != null && schedule.getPlanDay().getScheduleMode() == ScheduleMode.SIMPLE
        );
        if (simpleMode) {
            for (int i = 0; i < schedules.size(); i++) {
                DaySchedule current = schedules.get(i);
                current.setScheduleOrder(i);
                current.setEndTime(current.getStartTime() == null
                        ? null
                        : current.getStartTime().plusMinutes(current.getDuration()));
            }
            return schedules;
        }

        for (int i = 0; i < schedules.size(); i++) {
            DaySchedule current = schedules.get(i);
            current.setScheduleOrder(i);

            if (i == 0 || current.isFixedStartTime()) {
                // 첫 일정의 시작 시간은 보존, 종료 시간만 갱신
                current.setEndTime(current.getStartTime() == null
                        ? null
                        : current.getStartTime().plusMinutes(current.getDuration()));
            } else {
                DaySchedule prev = schedules.get(i - 1);
                if (prev.getEndTime() == null) {
                    current.setEndTime(current.getStartTime() == null
                            ? null
                            : current.getStartTime().plusMinutes(current.getDuration()));
                    continue;
                }
                LocalTime nextStart = prev.getEndTime().plusMinutes(current.getMovingDuration());

                current.setStartTime(nextStart);
                current.setEndTime(nextStart.plusMinutes(current.getDuration()));
            }
        }
        return schedules;
    }

    public List<DayScheduleResponse> applyRouteEstimates(Long userId, Long dayId, DayRouteApplyRequest request) {
        PlanDay day = findDayByUserIdAndId(userId, dayId);
        if (request == null || request.routes() == null || request.routes().isEmpty()) {
            throw new IllegalArgumentException("적용할 경로 계산 결과가 없습니다.");
        }
        if (request.routes().size() > 100) {
            throw new IllegalArgumentException("한 번에 최대 100개 이동 구간을 적용할 수 있습니다.");
        }

        List<DaySchedule> schedules = dayScheduleRepository
                .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, dayId);
        Map<Long, DaySchedule> byId = schedules.stream()
                .collect(Collectors.toMap(DaySchedule::getId, Function.identity()));
        HashSet<Long> appliedIds = new HashSet<>();
        for (DayRouteApplyRequest.RouteDuration route : request.routes()) {
            if (route == null || route.scheduleId() == null || route.estimatedDurationMinutes() == null) {
                throw new IllegalArgumentException("적용할 이동시간 정보가 올바르지 않습니다.");
            }
            if (!appliedIds.add(route.scheduleId())) {
                throw new IllegalArgumentException("같은 이동 구간이 중복되었습니다.");
            }
            if (route.estimatedDurationMinutes() < 0 || route.estimatedDurationMinutes() > 24 * 60) {
                throw new IllegalArgumentException("예상 이동시간은 0분 이상 1440분 이하여야 합니다.");
            }
            DaySchedule target = byId.get(route.scheduleId());
            if (target == null || target.getPlanDay() == null || !target.getPlanDay().getId().equals(day.getId())) {
                throw new EntityNotFoundException("적용할 이동 구간을 찾을 수 없습니다.");
            }
            target.setMovingDuration(
                    route.estimatedDurationMinutes() + Math.max(0, target.getExtraMovingDuration())
            );
        }

        List<DaySchedule> updated = recalculateTimesForDay(null, null, schedules);
        dayScheduleRepository.saveAll(updated);
        return updated.stream().map(DayScheduleResponse::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<UnlinkedSpotGroupResponse> getUnlinkedSpotGroups(Long userId, Long planId) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        LinkedHashMap<String, UnlinkedSpotGroupResponse> groups = new LinkedHashMap<>();
        dayScheduleRepository
                .findAllByUserIdAndPlanDayPlanIdAndSpotUserIsNullOrderByPlanDayDayOrderAscScheduleOrderAsc(userId, planId)
                .stream()
                .map(DaySchedule::getSpotNameSnapshot)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(name -> {
                    String key = name.toLowerCase(Locale.ROOT);
                    UnlinkedSpotGroupResponse current = groups.get(key);
                    groups.put(key, new UnlinkedSpotGroupResponse(
                            current == null ? name : current.spotName(),
                            current == null ? 1L : current.scheduleCount() + 1
                    ));
                });
        return List.copyOf(groups.values());
    }

    @Transactional
    public int linkSchedulesToSpot(Long userId, Long planId, ScheduleSpotLinkRequest req) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        if (req.sourceSpotName() == null || req.sourceSpotName().isBlank() || req.spotUserId() == null) {
            throw new IllegalArgumentException("연결할 일정 장소와 내 장소를 선택해 주세요.");
        }
        SpotUser target = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                .orElseThrow(() -> new EntityNotFoundException("내 장소를 찾을 수 없습니다."));
        List<DaySchedule> matches = dayScheduleRepository
                .findAllByUserIdAndPlanDayPlanIdAndSpotUserIsNullOrderByPlanDayDayOrderAscScheduleOrderAsc(userId, planId)
                .stream()
                .filter(schedule -> schedule.getSpotNameSnapshot() != null
                        && schedule.getSpotNameSnapshot().trim().equalsIgnoreCase(req.sourceSpotName().trim()))
                .toList();
        for (DaySchedule schedule : matches) {
            schedule.setSpotUser(target);
            schedule.setSpotNameSnapshot(target.getCustomName() == null || target.getCustomName().isBlank()
                    ? target.getSpot().getSpotName()
                    : target.getCustomName());
            schedule.setSpotLocationSnapshot((org.locationtech.jts.geom.Point) target.getSpot().getLocation().copy());
            schedule.setSpotTypeSnapshot(target.getSpotType());
        }
        dayScheduleRepository.saveAll(matches);
        return matches.size();
    }

    @Transactional
    public void updateSkip(Long userId, Long scheduleId) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        boolean newSkippedState = !Boolean.TRUE.equals(schedule.getIsSkipped());
        schedule.setIsSkipped(newSkippedState);
        if (newSkippedState && Boolean.TRUE.equals(schedule.getIsChecked())) {
            schedule.setIsChecked(false);
            if (schedule.getSpotUser() != null) {
                spotVisitHistoryRepository.deleteByUserIdAndSpotUserIdAndDayId(
                        userId,
                        schedule.getSpotUser().getId(),
                        schedule.getPlanDay().getId()
                );
            }
        }
    }

    private void adjustSimpleDurations(List<DaySchedule> schedules) {
        for (int i = 0; i < schedules.size() - 1; i++) {
            DaySchedule current = schedules.get(i);
            DaySchedule next = schedules.get(i + 1);
            if (current.getStartTime() == null || next.getStartTime() == null || !next.isFixedStartTime()) continue;

            long minutes = Duration.between(current.getStartTime(), next.getStartTime()).toMinutes();
            if (minutes > 0 && minutes <= 24 * 60) current.setDuration((int) minutes);
        }
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
