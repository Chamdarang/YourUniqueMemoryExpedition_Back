package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import study.yume.dto.route.request.RouteEstimateRequest;
import study.yume.dto.route.response.DayRouteAuditLegResponse;
import study.yume.dto.route.response.DayRouteAuditResponse;
import study.yume.dto.route.response.RouteEstimateResponse;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.enums.Transportation;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DayRouteAuditService {

    private static final DateTimeFormatter DEPARTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final RouteEstimateService routeEstimateService;

    public DayRouteAuditResponse audit(Long userId, Long dayId) {
        PlanDay day = planDayRepository.findOneByUserIdAndId(userId, dayId)
                .orElseThrow(() -> new EntityNotFoundException("해당 하루 일정을 찾을 수 없습니다."));
        List<DaySchedule> schedules = dayScheduleRepository
                .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, dayId);
        if (schedules.size() > 101) {
            throw new IllegalArgumentException("하루 전체 경로는 한 번에 최대 100개 구간까지 점검할 수 있습니다.");
        }

        List<DayRouteAuditLegResponse> legs = new ArrayList<>();
        for (int index = 1; index < schedules.size(); index++) {
            legs.add(auditLeg(userId, schedules.get(index - 1), schedules.get(index), routeDate(day)));
        }

        int calculated = (int) legs.stream().filter(leg -> leg.estimatedDurationMinutes() != null).count();
        int issues = (int) legs.stream().filter(leg -> !"OK".equals(leg.status())).count();
        int plannedTotal = legs.stream().mapToInt(DayRouteAuditLegResponse::plannedDurationMinutes).sum();
        int estimatedTotal = legs.stream()
                .map(DayRouteAuditLegResponse::estimatedDurationMinutes)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        return new DayRouteAuditResponse(
                legs.size(), calculated, issues, plannedTotal, estimatedTotal, List.copyOf(legs)
        );
    }

    private DayRouteAuditLegResponse auditLeg(Long userId, DaySchedule from, DaySchedule to, LocalDate routeDate) {
        Transportation transportation = to.getTransportation();
        int plannedDuration = Math.max(0, to.getMovingDuration());
        if (location(from) == null || location(to) == null) {
            return unavailable(from, to, transportation, plannedDuration,
                    "ERROR", "출발지 또는 도착지의 위치 정보가 없습니다.");
        }
        if (transportation == null) {
            return unavailable(from, to, null, plannedDuration,
                    "ERROR", "이동수단이 지정되지 않았습니다.");
        }
        if (transportation == Transportation.BUS
                || transportation == Transportation.SHIP
                || transportation == Transportation.AIRPLANE) {
            return unavailable(from, to, transportation, plannedDuration,
                    "WARNING", "이 이동수단은 자동 계산을 지원하지 않아 직접 확인해야 합니다.");
        }

        Point origin = location(from);
        Point destination = location(to);
        LocalTime departure = endTime(from);
        try {
            RouteEstimateResponse estimate = routeEstimateService.estimate(userId, new RouteEstimateRequest(
                    origin.getY(), origin.getX(), destination.getY(), destination.getX(), transportation,
                    departureTime(routeDate, departure)
            ));
            LocalTime arrival = departure == null ? null : departure.plusMinutes(estimate.durationMinutes());
            Integer conflict = fixedStartConflictMinutes(departure, estimate.durationMinutes(), to);
            int difference = estimate.durationMinutes() - plannedDuration;
            String status = conflict != null && conflict > 0 ? "WARNING" : "OK";
            String message = conflict != null && conflict > 0
                    ? "고정 시작시간보다 " + conflict + "분 늦게 도착할 수 있습니다."
                    : difference > 0
                    ? "입력한 이동시간보다 약 " + difference + "분 더 필요합니다."
                    : "계획한 시간 안에 이동할 수 있습니다.";
            if (difference > 0 && "OK".equals(status)) status = "WARNING";
            return new DayRouteAuditLegResponse(
                    from.getId(), spotName(from), to.getId(), spotName(to), transportation,
                    plannedDuration, estimate.durationMinutes(), estimate.encodedPolyline(), difference, arrival, conflict, status, message
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "경로를 계산하지 못했습니다."
                    : exception.getMessage();
            return unavailable(from, to, transportation, plannedDuration, "ERROR", message);
        }
    }

    private DayRouteAuditLegResponse unavailable(
            DaySchedule from,
            DaySchedule to,
            Transportation transportation,
            int plannedDuration,
            String status,
            String message
    ) {
        return new DayRouteAuditLegResponse(
                from.getId(), spotName(from), to.getId(), spotName(to), transportation,
                plannedDuration, null, "", null, null, null, status, message
        );
    }

    private Point location(DaySchedule schedule) {
        Point point = schedule.getSpotLocationSnapshot();
        if (point == null || point.isEmpty()) return null;
        double lat = point.getY();
        double lng = point.getX();
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || Math.abs(lat) > 90 || Math.abs(lng) > 180
                || (lat == 0 && lng == 0)) return null;
        return point;
    }

    private String spotName(DaySchedule schedule) {
        return schedule.getSpotNameSnapshot() == null || schedule.getSpotNameSnapshot().isBlank()
                ? "이름 없는 장소"
                : schedule.getSpotNameSnapshot();
    }

    private LocalTime endTime(DaySchedule schedule) {
        if (schedule.getEndTime() != null) return schedule.getEndTime();
        if (schedule.getStartTime() == null) return null;
        return schedule.getStartTime().plusMinutes(Math.max(0, schedule.getDuration()));
    }

    private String departureTime(LocalDate routeDate, LocalTime departure) {
        if (departure == null) return null;
        LocalDate effectiveDate = routeDate == null
                ? LocalDate.now(ZoneId.of("Asia/Tokyo"))
                : routeDate;
        return effectiveDate.atTime(departure).format(DEPARTURE_FORMAT);
    }

    private Integer fixedStartConflictMinutes(LocalTime departure, int estimatedDuration, DaySchedule destination) {
        if (departure == null || destination.getStartTime() == null || !destination.isFixedStartTime()) return null;
        int departureMinute = departure.getHour() * 60 + departure.getMinute();
        int fixedMinute = destination.getStartTime().getHour() * 60 + destination.getStartTime().getMinute();
        while (fixedMinute < departureMinute) fixedMinute += 24 * 60;
        return Math.max(0, departureMinute + estimatedDuration - fixedMinute);
    }

    private LocalDate routeDate(PlanDay day) {
        if (day.getPlan() == null || day.getPlan().getPlanStartDate() == null) return null;
        return day.getPlan().getPlanStartDate().plusDays(Math.max(0, day.getDayOrder() - 1L));
    }
}
