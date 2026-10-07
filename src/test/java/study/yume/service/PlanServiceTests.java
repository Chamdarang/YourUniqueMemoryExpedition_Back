package study.yume.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.plan.request.PlanUpdateRequest;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTests {
    @Mock private PlanRepository planRepository;
    @Mock private PlanDayRepository planDayRepository;
    @Mock private PlanDayService planDayService;
    @Mock private PlanAccessService planAccessService;
    @Mock private PlanLiveUpdateService planLiveUpdateService;

    @Test
    void rejectsShorteningPlanWhenOutOfRangeDaysAreNotDetached() {
        Plan plan = plan();
        PlanDay thirdDay = day(31L, plan, 3);
        when(planAccessService.requireEditor(1L, 20L)).thenReturn(plan);
        when(planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(1L, 20L)).thenReturn(List.of(thirdDay));

        assertThatThrownBy(() -> service().updatePlan(1L, 20L, update(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("여행 기간 밖의 일차");

        assertThat(thirdDay.getPlan()).isSameAs(plan);
        verify(planRepository, never()).save(plan);
    }

    @Test
    void detachesEveryOutOfRangeDayBeforeSavingShorterPlan() {
        Plan plan = plan();
        PlanDay firstDay = day(21L, plan, 1);
        PlanDay thirdDay = day(23L, plan, 3);
        when(planAccessService.requireEditor(1L, 20L)).thenReturn(plan);
        when(planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(1L, 20L)).thenReturn(List.of(firstDay, thirdDay));
        when(planRepository.save(plan)).thenReturn(plan);

        service().updatePlan(1L, 20L, update(List.of(23L)));

        assertThat(firstDay.getPlan()).isSameAs(plan);
        assertThat(thirdDay.getPlan()).isNull();
        assertThat(thirdDay.getDayOrder()).isEqualTo(1);
        assertThat(plan.getPlanDays()).isEqualTo(2);
        verify(planDayRepository).saveAll(List.of(thirdDay));
        verify(planLiveUpdateService).publishAfterCommit(20L, 1L);
    }

    private PlanService service() {
        return new PlanService(planRepository, planDayRepository, planDayService, planAccessService, planLiveUpdateService);
    }

    private PlanUpdateRequest update(List<Long> detachDayIds) {
        return new PlanUpdateRequest("Trip", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), 2,
                "", 7L, detachDayIds);
    }

    private Plan plan() {
        Plan plan = new Plan();
        plan.setId(20L);
        plan.setUserId(1L);
        plan.setPlanName("Trip");
        plan.setVersion(7L);
        plan.setPlanDays(4);
        plan.setPlanStartDate(LocalDate.of(2026, 10, 1));
        plan.setPlanEndDate(LocalDate.of(2026, 10, 4));
        return plan;
    }

    private PlanDay day(Long id, Plan plan, int order) {
        PlanDay day = new PlanDay();
        day.setId(id);
        day.setUserId(1L);
        day.setPlan(plan);
        day.setDayOrder(order);
        day.setDayName("Day " + order);
        return day;
    }
}
