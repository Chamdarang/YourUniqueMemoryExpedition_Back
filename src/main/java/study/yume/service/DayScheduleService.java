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
import study.yume.exception.ScheduleEditConflictException;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.SpotUser;
import study.yume.model.SpotVisitHistory;
import study.yume.model.ScheduleVisitStatus;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;
import study.yume.repository.ScheduleVisitStatusRepository;

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
    private final ScheduleVisitStatusRepository scheduleVisitStatusRepository;
    private final PlanAccessService planAccessService;
    private final PlanLiveUpdateService planLiveUpdateService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional(readOnly = true)
    public List<DayScheduleResponse> getSchedulesByDayId(Long userId, Long dayId) {
        PlanDay day = findDayByUserIdAndId(userId, dayId);

        return toScheduleResponses(
                dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(day.getUserId(), dayId),
                userId
        );
    }

    public List<DayScheduleResponse> createSchedule(Long userId, Long dayId, ScheduleCreateRequest req) {
        PlanDay planDay = findDayByUserIdAndId(userId, dayId);
        Long dataOwnerId = planDay.getUserId();
        List<DaySchedule> schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(dataOwnerId, dayId);

        if (req.scheduleOrder() < 0 || req.scheduleOrder() > schedules.size()) {
            throw new IllegalArgumentException("일정을 추가할 위치가 올바르지 않습니다.");
        }

        DaySchedule daySchedule = new DaySchedule();
        daySchedule.setUserId(dataOwnerId);
        daySchedule.setPlanDay(planDay);
        daySchedule.setScheduleOrder(req.scheduleOrder());
        daySchedule.setStartTime(req.startTime() != null
                ? req.startTime()
                : planDay.getScheduleMode() == ScheduleMode.SIMPLE ? null : LocalTime.of(9, 0));
        daySchedule.setFixedStartTime(req.startTime() != null);
        daySchedule.setDuration(60);
        daySchedule.setMemo(req.memo());
        Long personalSpotId = null;

        if (req.spotUserId() != null) {
            SpotUser spotUser = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                    .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다."));
            if (userId.equals(dataOwnerId)) daySchedule.setSpotUser(spotUser);
            applySpotSnapshot(daySchedule, spotUser);
            personalSpotId = spotUser.getSpot().getId();
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
        if (personalSpotId != null) rememberPersonalSpot(userId, daySchedule, personalSpotId);
        if (planDay.getPlan() != null) planLiveUpdateService.publishAfterCommit(planDay.getPlan().getId(), userId);
        return toScheduleResponses(updatedSchedules, userId);
    }

    public List<DayScheduleResponse> updateSchedule(Long userId, Long scheduleId, ScheduleUpdateRequest req) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        Long dataOwnerId = schedule.getUserId();
        if (req.expectedVersion() == null) {
            throw new IllegalArgumentException("일정 버전 정보가 없습니다. 최신 일정을 다시 불러와 주세요.");
        }
        if (!req.expectedVersion().equals(schedule.getVersion())) {
            throw new ScheduleEditConflictException(
                    "다른 사용자가 이 일정을 수정했습니다.",
                    DayScheduleResponse.toDto(schedule, userId.equals(dataOwnerId))
            );
        }

        if (req.spotUserId() != null) {
            if (req.spotUserId() == 0) {
                if (userId.equals(dataOwnerId)) schedule.setSpotUser(null);
                rememberPersonalSpot(userId, schedule, null);
            } else {
                SpotUser spotUser = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                    .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다."));
                schedule.setSpotUser(userId.equals(dataOwnerId) ? spotUser : null);
                applySpotSnapshot(schedule, spotUser);
                rememberPersonalSpot(userId, schedule, spotUser.getSpot().getId());
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
                dataOwnerId,
                schedule.getPlanDay().getId()
        );
        if (schedule.getPlanDay().getScheduleMode() == ScheduleMode.SIMPLE) adjustSimpleDurations(schedules);
        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null, null, schedules);
        dayScheduleRepository.saveAll(updatedSchedules);

        if (schedule.getPlanDay().getPlan() != null) {
            planLiveUpdateService.publishAfterCommit(schedule.getPlanDay().getPlan().getId(), userId);
        }
        return toScheduleResponses(updatedSchedules, userId);
    }

    public List<DayScheduleResponse> reorderSchedule(Long userId, Long dayId, Long scheduleId, ScheduleReorderRequest req) {
        PlanDay day = findDayByUserIdAndId(userId, dayId);
        List<DaySchedule> schedules = dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(day.getUserId(), dayId);

        DaySchedule target = schedules.stream()
                .filter(s -> s.getId().equals(scheduleId))
                .findFirst()
                .orElseThrow();

        schedules.remove(target);
        schedules.add(req.scheduleOrder(), target); // 인덱스 기반 이동

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(null, null,schedules);
        dayScheduleRepository.saveAll(updatedSchedules);

        if (day.getPlan() != null) planLiveUpdateService.publishAfterCommit(day.getPlan().getId(), userId);
        return toScheduleResponses(updatedSchedules, userId);
    }

    public ScheduleTransferResponse transferSchedule(Long userId, Long scheduleId, ScheduleTransferRequest req) {
        if (req == null || req.targetDayId() == null) {
            throw new IllegalArgumentException("대상 일차를 선택해 주세요.");
        }

        DaySchedule source = findScheduleByUserIdAndId(userId, scheduleId);
        PlanDay sourceDay = source.getPlanDay();
        PlanDay targetDay = findDayByUserIdAndId(userId, req.targetDayId());
        if (!sourceDay.getUserId().equals(targetDay.getUserId())) {
            throw new IllegalArgumentException("일정은 같은 소유자의 계획 사이에서만 이동할 수 있습니다.");
        }
        if (sourceDay.getId().equals(targetDay.getId())) {
            throw new IllegalArgumentException("현재 일차가 아닌 다른 일차를 선택해 주세요.");
        }

        List<DaySchedule> sourceSchedules = new java.util.ArrayList<>(
                dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(sourceDay.getUserId(), sourceDay.getId())
        );
        List<DaySchedule> targetSchedules = new java.util.ArrayList<>(
                dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(targetDay.getUserId(), targetDay.getId())
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

        if (sourceDay.getPlan() != null) planLiveUpdateService.publishAfterCommit(sourceDay.getPlan().getId(), userId);
        if (targetDay.getPlan() != null && (sourceDay.getPlan() == null
                || !targetDay.getPlan().getId().equals(sourceDay.getPlan().getId()))) {
            planLiveUpdateService.publishAfterCommit(targetDay.getPlan().getId(), userId);
        }

        return new ScheduleTransferResponse(
                transferred.getId(),
                sourceDay.getId(),
                targetDay.getId(),
                toScheduleResponses(sourceSchedules, userId),
                toScheduleResponses(targetSchedules, userId)
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
        Long dataOwnerId = schedule.getUserId();
        Long planId = schedule.getPlanDay().getPlan() == null ? null : schedule.getPlanDay().getPlan().getId();
        dayScheduleRepository.delete(schedule);

        List<DaySchedule> updatedSchedules= recalculateTimesForDay(dataOwnerId, schedule.getPlanDay().getId(),null);
        dayScheduleRepository.saveAll(updatedSchedules);
        planLiveUpdateService.publishAfterCommit(planId, userId);
        return toScheduleResponses(updatedSchedules, userId);
    }


    @Transactional
    public void updateVisit(Long userId, Long scheduleId){
        DaySchedule schedule = findScheduleByUserIdAndId(userId,scheduleId);
        ScheduleVisitStatus status = scheduleVisitStatusRepository.findByUserIdAndScheduleId(userId, scheduleId)
                .orElseGet(() -> newVisitStatus(userId, schedule));
        status.setChecked(!status.isChecked());
        if (status.isChecked()) status.setSkipped(false);
        saveOrRemoveStatus(status);
        syncPersonalSpotVisit(schedule, userId, status.isChecked());
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
                .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(day.getUserId(), dayId);
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
        if (day.getPlan() != null) planLiveUpdateService.publishAfterCommit(day.getPlan().getId(), userId);
        return toScheduleResponses(updated, userId);
    }

    @Transactional(readOnly = true)
    public List<UnlinkedSpotGroupResponse> getUnlinkedSpotGroups(Long userId, Long planId) {
        Long dataOwnerId = planAccessService.requireEditorOwnerId(userId, planId);
        LinkedHashMap<String, UnlinkedSpotGroupResponse> groups = new LinkedHashMap<>();
        dayScheduleRepository
                .findAllByUserIdAndPlanDayPlanIdAndSpotUserIsNullOrderByPlanDayDayOrderAscScheduleOrderAsc(dataOwnerId, planId)
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
        Long dataOwnerId = planAccessService.requireEditorOwnerId(userId, planId);
        if (req.sourceSpotName() == null || req.sourceSpotName().isBlank() || req.spotUserId() == null) {
            throw new IllegalArgumentException("연결할 일정 장소와 내 장소를 선택해 주세요.");
        }
        SpotUser target = spotUserRepository.findByUserIdAndId(userId, req.spotUserId())
                .orElseThrow(() -> new EntityNotFoundException("내 장소를 찾을 수 없습니다."));
        List<DaySchedule> matches = dayScheduleRepository
                .findAllByUserIdAndPlanDayPlanIdAndSpotUserIsNullOrderByPlanDayDayOrderAscScheduleOrderAsc(dataOwnerId, planId)
                .stream()
                .filter(schedule -> schedule.getSpotNameSnapshot() != null
                        && schedule.getSpotNameSnapshot().trim().equalsIgnoreCase(req.sourceSpotName().trim()))
                .toList();
        for (DaySchedule schedule : matches) {
            if (userId.equals(dataOwnerId)) schedule.setSpotUser(target);
            applySpotSnapshot(schedule, target);
            rememberPersonalSpot(userId, schedule, target.getSpot().getId());
        }
        dayScheduleRepository.saveAll(matches);
        planLiveUpdateService.publishAfterCommit(planId, userId);
        return matches.size();
    }

    @Transactional
    public void updateSkip(Long userId, Long scheduleId) {
        DaySchedule schedule = findScheduleByUserIdAndId(userId, scheduleId);
        ScheduleVisitStatus status = scheduleVisitStatusRepository.findByUserIdAndScheduleId(userId, scheduleId)
                .orElseGet(() -> newVisitStatus(userId, schedule));
        status.setSkipped(!status.isSkipped());
        if (status.isSkipped()) {
            status.setChecked(false);
            syncPersonalSpotVisit(schedule, userId, false);
        }
        saveOrRemoveStatus(status);
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
        DaySchedule schedule = dayScheduleRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        PlanDay day = schedule.getPlanDay();
        if (day == null) throw new EntityNotFoundException("일정을 찾을 수 없습니다.");
        if (day.getPlan() == null) {
            if (!day.getUserId().equals(userId) || !schedule.getUserId().equals(userId)) {
                throw new EntityNotFoundException("일정을 찾을 수 없습니다.");
            }
            return schedule;
        }
        Long ownerId = planAccessService.requireEditorOwnerId(userId, day.getPlan().getId());
        if (!day.getUserId().equals(ownerId) || !schedule.getUserId().equals(ownerId)) {
            throw new EntityNotFoundException("일정을 찾을 수 없습니다.");
        }
        return schedule;
    }

    private PlanDay findDayByUserIdAndId(Long userId, Long planId) {
        PlanDay day = plandayRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("해당 하루 일정을 찾을 수 없습니다."));
        if (day.getPlan() == null) {
            if (!day.getUserId().equals(userId)) throw new EntityNotFoundException("해당 하루 일정을 찾을 수 없습니다.");
            return day;
        }
        Long ownerId = planAccessService.requireEditorOwnerId(userId, day.getPlan().getId());
        if (!day.getUserId().equals(ownerId)) throw new EntityNotFoundException("해당 하루 일정을 찾을 수 없습니다.");
        return day;
    }

    private List<DayScheduleResponse> toScheduleResponses(List<DaySchedule> schedules, Long actorUserId) {
        if (schedules.isEmpty()) return List.of();
        List<Long> scheduleIds = schedules.stream().map(DaySchedule::getId).toList();
        Map<Long, ScheduleVisitStatus> statuses = scheduleVisitStatusRepository
                .findAllByUserIdAndScheduleIdIn(actorUserId, scheduleIds).stream()
                .collect(Collectors.toMap(ScheduleVisitStatus::getScheduleId, Function.identity()));
        return schedules.stream()
                .map(schedule -> {
                    ScheduleVisitStatus status = statuses.get(schedule.getId());
                    return DayScheduleResponse.toDto(
                            schedule,
                            actorUserId.equals(schedule.getUserId()),
                            status != null && status.isChecked(),
                            status != null && status.isSkipped()
                    );
                })
                .toList();
    }

    private ScheduleVisitStatus newVisitStatus(Long userId, DaySchedule schedule) {
        ScheduleVisitStatus status = new ScheduleVisitStatus();
        status.setUserId(userId);
        status.setScheduleId(schedule.getId());
        if (schedule.getSpotUser() != null) status.setSpotId(schedule.getSpotUser().getSpot().getId());
        return status;
    }

    private void saveOrRemoveStatus(ScheduleVisitStatus status) {
        if (!status.isChecked() && !status.isSkipped() && status.getSpotId() == null && !status.isSpotLinkCleared()) {
            scheduleVisitStatusRepository.deleteByUserIdAndScheduleId(status.getUserId(), status.getScheduleId());
        } else {
            scheduleVisitStatusRepository.save(status);
        }
    }

    private void syncPersonalSpotVisit(DaySchedule schedule, Long userId, boolean isChecked) {
        ScheduleVisitStatus status = scheduleVisitStatusRepository.findByUserIdAndScheduleId(userId, schedule.getId()).orElse(null);
        Long spotId = status == null
                ? schedule.getSpotUser() == null ? null : schedule.getSpotUser().getSpot().getId()
                : status.isSpotLinkCleared() ? null
                : status.getSpotId() == null
                        ? schedule.getSpotUser() == null ? null : schedule.getSpotUser().getSpot().getId()
                        : status.getSpotId();
        SpotUser personalSpot = spotId == null ? null
                : spotUserRepository.findByUserIdAndSpotId(userId, spotId).orElse(null);
        if (!isChecked) {
            spotVisitHistoryRepository.deleteByUserIdAndScheduleId(userId, schedule.getId());
            return;
        }
        if (personalSpot == null) return;

        personalSpot.setIsVisit(true);
        if (spotVisitHistoryRepository.existsByUserIdAndScheduleId(userId, schedule.getId())) return;
        SpotVisitHistory history = new SpotVisitHistory();
        history.setUserId(userId);
        history.setScheduleId(schedule.getId());
        history.setSpotUser(personalSpot);
        history.setDayId(schedule.getPlanDay().getId());
        history.setDayNameSnapshot(schedule.getPlanDay().getDayName());
        PlanDay day = schedule.getPlanDay();
        LocalDate startDate = day.getPlan() == null ? null : day.getPlan().getPlanStartDate();
        history.setVisitedAt(startDate == null ? LocalDate.now() : startDate.plusDays(day.getDayOrder() - 1L));
        if (day.getPlan() != null) {
            history.setPlanId(day.getPlan().getId());
            history.setPlanNameSnapshot(day.getPlan().getPlanName());
        }
        spotVisitHistoryRepository.save(history);
    }

    private void rememberPersonalSpot(Long userId, DaySchedule schedule, Long spotId) {
        ScheduleVisitStatus status = scheduleVisitStatusRepository.findByUserIdAndScheduleId(userId, schedule.getId())
                .orElseGet(() -> newVisitStatus(userId, schedule));
        status.setSpotId(spotId);
        status.setSpotLinkCleared(spotId == null);
        saveOrRemoveStatus(status);
    }

    private void applySpotSnapshot(DaySchedule schedule, SpotUser spotUser) {
        schedule.setSpotNameSnapshot(spotUser.getCustomName() == null || spotUser.getCustomName().isBlank()
                ? spotUser.getSpot().getSpotName()
                : spotUser.getCustomName());
        schedule.setSpotLocationSnapshot((org.locationtech.jts.geom.Point) spotUser.getSpot().getLocation().copy());
        schedule.setSpotTypeSnapshot(spotUser.getSpotType());
    }



}
