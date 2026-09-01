package study.yume.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.route.response.DayRouteAuditResponse;
import study.yume.dto.route.response.RouteEstimateResponse;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.enums.Transportation;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayRouteAuditServiceTests {
    @Mock private PlanDayRepository planDayRepository;
    @Mock private DayScheduleRepository dayScheduleRepository;
    @Mock private RouteEstimateService routeEstimateService;

    @Test
    void reportsDelayAgainstFixedStartWithoutChangingSchedule() {
        PlanDay day = new PlanDay();
        day.setId(7L);
        day.setUserId(3L);
        day.setDayOrder(1);

        DaySchedule from = schedule(10L, day, 0, "출발지", 135.75, 35.01);
        from.setStartTime(LocalTime.of(9, 0));
        from.setEndTime(LocalTime.of(10, 0));
        DaySchedule to = schedule(11L, day, 1, "도착지", 135.80, 35.03);
        to.setIsSkipped(true);
        to.setTransportation(Transportation.TRAIN);
        to.setMovingDuration(20);
        to.setStartTime(LocalTime.of(10, 30));
        to.setFixedStartTime(true);

        when(planDayRepository.findOneByUserIdAndId(3L, 7L)).thenReturn(Optional.of(day));
        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(3L, 7L))
                .thenReturn(List.of(from, to));
        when(routeEstimateService.estimate(anyLong(), any()))
                .thenReturn(new RouteEstimateResponse(45, 12_000, "", ""));

        DayRouteAuditResponse result = new DayRouteAuditService(
                planDayRepository, dayScheduleRepository, routeEstimateService
        ).audit(3L, 7L);

        assertThat(result.totalLegs()).isEqualTo(1);
        assertThat(result.issueCount()).isEqualTo(1);
        assertThat(result.legs().get(0).fixedStartConflictMinutes()).isEqualTo(15);
        assertThat(result.legs().get(0).status()).isEqualTo("WARNING");
        assertThat(to.getMovingDuration()).isEqualTo(20);
    }

    private DaySchedule schedule(Long id, PlanDay day, int order, String name, double lng, double lat) {
        DaySchedule schedule = new DaySchedule();
        schedule.setId(id);
        schedule.setUserId(day.getUserId());
        schedule.setPlanDay(day);
        schedule.setScheduleOrder(order);
        schedule.setSpotNameSnapshot(name);
        schedule.setSpotLocationSnapshot(
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(lng, lat))
        );
        schedule.setIsSkipped(false);
        return schedule;
    }
}
