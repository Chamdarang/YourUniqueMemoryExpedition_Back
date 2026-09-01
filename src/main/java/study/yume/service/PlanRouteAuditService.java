package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import study.yume.dto.route.response.PlanRouteAuditDayResponse;
import study.yume.dto.route.response.PlanRouteAuditResponse;
import study.yume.dto.route.response.PlanScheduleAuditIssueResponse;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlanRouteAuditService {

    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final int maxRouteCalculationLegs;

    public PlanRouteAuditService(
            PlanRepository planRepository,
            PlanDayRepository planDayRepository,
            DayScheduleRepository dayScheduleRepository,
            @Value("${route.estimate.plan-audit-max-legs:50}") int maxRouteCalculationLegs
    ) {
        this.planRepository = planRepository;
        this.planDayRepository = planDayRepository;
        this.dayScheduleRepository = dayScheduleRepository;
        this.maxRouteCalculationLegs = Math.max(1, maxRouteCalculationLegs);
    }

    public PlanRouteAuditResponse audit(Long userId, Long planId) {
        Plan plan = planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        Map<Integer, PlanDay> daysByOrder = new HashMap<>();
        planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(userId, planId)
                .forEach(day -> daysByOrder.put(day.getDayOrder(), day));
        Map<Long, List<DaySchedule>> schedulesByDay = dayScheduleRepository
                .findAllByUserIdAndPlanDayPlanIdOrderByPlanDayDayOrderAscScheduleOrderAsc(userId, planId)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        schedule -> schedule.getPlanDay().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        int maxExistingDayOrder = daysByOrder.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int totalDayCount = Math.max(plan.getPlanDays() == null ? 0 : plan.getPlanDays(), maxExistingDayOrder);
        List<DayData> dayData = new ArrayList<>();
        int totalSchedules = 0;
        int totalLegs = 0;
        for (int dayOrder = 1; dayOrder <= totalDayCount; dayOrder++) {
            PlanDay day = daysByOrder.get(dayOrder);
            List<DaySchedule> schedules = day == null
                    ? List.of()
                    : schedulesByDay.getOrDefault(day.getId(), List.of());
            totalSchedules += schedules.size();
            totalLegs += Math.max(0, schedules.size() - 1);
            dayData.add(new DayData(dayOrder, day, schedules));
        }
        List<PlanRouteAuditDayResponse> responses = new ArrayList<>();
        int totalIssues = 0;
        for (DayData data : dayData) {
            List<PlanScheduleAuditIssueResponse> scheduleIssues = localIssues(data);
            int issueCount = scheduleIssues.size();
            totalIssues += issueCount;
            responses.add(new PlanRouteAuditDayResponse(
                    data.day() == null ? null : data.day().getId(),
                    data.dayOrder(),
                    data.day() == null ? data.dayOrder() + "일차" : data.day().getDayName(),
                    data.schedules().size(),
                    issueCount,
                    List.copyOf(scheduleIssues),
                    null
            ));
        }

        return new PlanRouteAuditResponse(
                false,
                maxRouteCalculationLegs,
                totalDayCount,
                totalSchedules,
                totalLegs,
                totalIssues,
                List.copyOf(responses)
        );
    }

    private List<PlanScheduleAuditIssueResponse> localIssues(DayData data) {
        List<PlanScheduleAuditIssueResponse> issues = new ArrayList<>();
        if (data.day() == null || data.schedules().isEmpty()) {
            issues.add(new PlanScheduleAuditIssueResponse(
                    null, data.dayOrder() + "일차", "WARNING", "EMPTY_DAY", "등록된 일정이 없습니다."
            ));
            return issues;
        }

        List<DaySchedule> schedules = data.schedules();
        for (int index = 0; index < schedules.size(); index++) {
            DaySchedule schedule = schedules.get(index);
            String spotName = spotName(schedule);
            if (schedule.getSpotNameSnapshot() == null || schedule.getSpotNameSnapshot().isBlank()) {
                issues.add(issue(schedule, spotName, "ERROR", "MISSING_NAME", "장소명이 비어 있습니다."));
            }
            if (!hasValidLocation(schedule)) {
                issues.add(issue(schedule, spotName, "WARNING", "MISSING_LOCATION", "지도에 표시할 위치가 없습니다."));
            }
            if (data.day().getScheduleMode() == ScheduleMode.DETAILED
                    && (schedule.getStartTime() == null || schedule.getEndTime() == null)) {
                issues.add(issue(schedule, spotName, "WARNING", "MISSING_TIME", "상세 일정의 시작 또는 종료 시간이 비어 있습니다."));
            }
            if (index > 0 && schedule.getTransportation() == null) {
                issues.add(issue(schedule, spotName, "WARNING", "MISSING_TRANSPORTATION", "이전 장소에서 오는 이동수단이 지정되지 않았습니다."));
            }
            if (index > 0 && schedule.isFixedStartTime()) {
                int lateMinutes = plannedFixedStartConflict(schedules.get(index - 1), schedule);
                if (lateMinutes > 0) {
                    issues.add(issue(
                            schedule,
                            spotName,
                            "ERROR",
                            "FIXED_START_CONFLICT",
                            "현재 입력된 이동시간 기준으로 고정 시작시간보다 " + lateMinutes + "분 늦습니다."
                    ));
                }
            }
        }
        return issues;
    }

    private PlanScheduleAuditIssueResponse issue(
            DaySchedule schedule,
            String spotName,
            String severity,
            String code,
            String message
    ) {
        return new PlanScheduleAuditIssueResponse(schedule.getId(), spotName, severity, code, message);
    }

    private int plannedFixedStartConflict(DaySchedule previous, DaySchedule current) {
        LocalTime departure = previous.getEndTime();
        LocalTime fixedStart = current.getStartTime();
        if (departure == null || fixedStart == null) return 0;
        int departureMinute = departure.getHour() * 60 + departure.getMinute();
        int fixedMinute = fixedStart.getHour() * 60 + fixedStart.getMinute();
        while (fixedMinute < departureMinute) fixedMinute += 24 * 60;
        return Math.max(0, departureMinute + Math.max(0, current.getMovingDuration()) - fixedMinute);
    }

    private boolean hasValidLocation(DaySchedule schedule) {
        if (schedule.getSpotLocationSnapshot() == null || schedule.getSpotLocationSnapshot().isEmpty()) return false;
        double lat = schedule.getSpotLocationSnapshot().getY();
        double lng = schedule.getSpotLocationSnapshot().getX();
        return Double.isFinite(lat) && Double.isFinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180
                && (lat != 0 || lng != 0);
    }

    private String spotName(DaySchedule schedule) {
        return schedule.getSpotNameSnapshot() == null || schedule.getSpotNameSnapshot().isBlank()
                ? "이름 없는 장소"
                : schedule.getSpotNameSnapshot();
    }

    private record DayData(int dayOrder, PlanDay day, List<DaySchedule> schedules) {
    }
}
