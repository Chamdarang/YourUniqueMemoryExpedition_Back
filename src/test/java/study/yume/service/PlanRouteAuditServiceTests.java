package study.yume.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.route.response.PlanRouteAuditResponse;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanRouteAuditServiceTests {

    @Mock private PlanRepository planRepository;
    @Mock private PlanDayRepository planDayRepository;
    @Mock private DayScheduleRepository dayScheduleRepository;

    @Test
    void reportsLocalScheduleProblemsAndMissingPlanDayWithoutCallingProviders() {
        Plan plan = new Plan();
        plan.setId(10L);
        plan.setUserId(3L);
        plan.setPlanDays(2);

        PlanDay firstDay = new PlanDay();
        firstDay.setId(20L);
        firstDay.setUserId(3L);
        firstDay.setPlan(plan);
        firstDay.setDayOrder(1);
        firstDay.setDayName("첫날");
        firstDay.setScheduleMode(ScheduleMode.DETAILED);

        DaySchedule first = schedule(100L, firstDay, 0, "출발지", LocalTime.of(9, 0), LocalTime.of(10, 0));
        DaySchedule second = schedule(101L, firstDay, 1, "예약 장소", LocalTime.of(10, 15), LocalTime.of(11, 0));
        second.setIsSkipped(true);
        second.setFixedStartTime(true);
        second.setMovingDuration(30);

        when(planRepository.findByUserIdAndId(3L, 10L)).thenReturn(Optional.of(plan));
        when(planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(3L, 10L)).thenReturn(List.of(firstDay));
        when(dayScheduleRepository.findAllByUserIdAndPlanDayPlanIdOrderByPlanDayDayOrderAscScheduleOrderAsc(3L, 10L))
                .thenReturn(List.of(first, second));

        PlanRouteAuditResponse result = new PlanRouteAuditService(
                planRepository, planDayRepository, dayScheduleRepository, 50
        ).audit(3L, 10L);

        assertThat(result.routesCalculated()).isFalse();
        assertThat(result.totalDays()).isEqualTo(2);
        assertThat(result.totalLegs()).isEqualTo(1);
        assertThat(result.issueCount()).isEqualTo(5);
        assertThat(result.days().get(0).scheduleIssues())
                .extracting(issue -> issue.code())
                .containsExactly("MISSING_LOCATION", "MISSING_LOCATION", "MISSING_TRANSPORTATION", "FIXED_START_CONFLICT");
        assertThat(result.days().get(1).scheduleIssues().get(0).code()).isEqualTo("EMPTY_DAY");
    }

    private DaySchedule schedule(
            Long id,
            PlanDay day,
            int order,
            String name,
            LocalTime start,
            LocalTime end
    ) {
        DaySchedule schedule = new DaySchedule();
        schedule.setId(id);
        schedule.setUserId(day.getUserId());
        schedule.setPlanDay(day);
        schedule.setScheduleOrder(order);
        schedule.setSpotNameSnapshot(name);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setIsSkipped(false);
        return schedule;
    }
}
