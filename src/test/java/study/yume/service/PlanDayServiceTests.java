package study.yume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.planday.request.PlanDayCopyRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDayServiceTests {
    @Test
    void shiftsOnlyUntilFirstGapAndPreservesLastDay() {
        Plan plan = new Plan();
        plan.setId(30L);
        plan.setUserId(1L);
        plan.setPlanDays(5);
        PlanDay source = day(99L, null, 1);
        PlanDay second = day(2L, plan, 2);
        PlanDay third = day(3L, plan, 3);
        PlanDay fifth = day(5L, plan, 5);
        prepareShift(source, plan, List.of(second, third, fifth));

        service.swapPlanDay(1L, new study.yume.dto.planday.request.PlanDaySwapRequest(
                99L, 30L, 2, study.yume.model.enums.SwapMode.SHIFT));

        assertThat(source.getDayOrder()).isEqualTo(2);
        assertThat(second.getDayOrder()).isEqualTo(3);
        assertThat(third.getDayOrder()).isEqualTo(4);
        assertThat(fifth.getDayOrder()).isEqualTo(5);
    }

    @Test
    void usesVacatedSourceSlotWhenShiftingWithinFullPlan() {
        Plan plan = new Plan();
        plan.setId(30L);
        plan.setUserId(1L);
        plan.setPlanDays(3);
        PlanDay first = day(1L, plan, 1);
        PlanDay second = day(2L, plan, 2);
        PlanDay source = day(3L, plan, 3);
        prepareShift(source, plan, List.of(first, second, source));

        service.swapPlanDay(1L, new study.yume.dto.planday.request.PlanDaySwapRequest(
                3L, 30L, 1, study.yume.model.enums.SwapMode.SHIFT));

        assertThat(source.getDayOrder()).isEqualTo(1);
        assertThat(first.getDayOrder()).isEqualTo(2);
        assertThat(second.getDayOrder()).isEqualTo(3);
    }

    @Test
    void rejectsShiftWithoutRoomBeforeChangingAnyDay() {
        Plan plan = new Plan();
        plan.setId(30L);
        plan.setUserId(1L);
        plan.setPlanDays(2);
        PlanDay source = day(99L, null, 1);
        PlanDay first = day(1L, plan, 1);
        PlanDay second = day(2L, plan, 2);
        prepareShift(source, plan, List.of(first, second));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.swapPlanDay(1L,
                new study.yume.dto.planday.request.PlanDaySwapRequest(
                        99L, 30L, 1, study.yume.model.enums.SwapMode.SHIFT)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(first.getDayOrder()).isEqualTo(1);
        assertThat(second.getDayOrder()).isEqualTo(2);
        assertThat(source.getPlan()).isNull();
        verify(planDayRepository, org.mockito.Mockito.never()).save(any());
    }

    private PlanDay day(Long id, Plan plan, int order) {
        PlanDay day = new PlanDay();
        day.setId(id);
        day.setUserId(1L);
        day.setPlan(plan);
        day.setDayOrder(order);
        return day;
    }

    private void prepareShift(PlanDay source, Plan target, List<PlanDay> days) {
        when(planDayRepository.findById(source.getId())).thenReturn(Optional.of(source));
        if (source.getPlan() != null) when(planAccessService.requireEditorOwnerId(1L, source.getPlan().getId())).thenReturn(1L);
        when(planAccessService.requireEditor(1L, target.getId())).thenReturn(target);
        when(planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(1L, target.getId())).thenReturn(days);
    }

    @Mock private PlanDayRepository planDayRepository;
    @Mock private DayScheduleRepository dayScheduleRepository;
    @Mock private study.yume.repository.ScheduleVisitStatusRepository scheduleVisitStatusRepository;
    @Mock private PlanAccessService planAccessService;
    @Mock private PlanLiveUpdateService planLiveUpdateService;

    private PlanDayService service;

    @BeforeEach
    void setUp() {
        service = new PlanDayService(planDayRepository, dayScheduleRepository, scheduleVisitStatusRepository, planAccessService, planLiveUpdateService);
    }

    @Test
    void copiesDayIntoEmptyPlanSlotAndResetsProgress() {
        Plan sourcePlan = new Plan();
        sourcePlan.setId(10L);
        PlanDay source = new PlanDay();
        source.setId(20L);
        source.setUserId(1L);
        source.setPlan(sourcePlan);
        source.setDayName("교토 동부");
        source.setDayOrder(1);
        source.setScheduleMode(ScheduleMode.SIMPLE);

        DaySchedule original = new DaySchedule();
        original.setUserId(1L);
        original.setPlanDay(source);
        original.setScheduleOrder(0);
        original.setSpotNameSnapshot("청수사");
        original.setIsChecked(true);
        original.setIsSkipped(false);
        original.setStartTime(LocalTime.of(9, 0));
        original.setEndTime(LocalTime.of(10, 0));
        original.setDuration(60);

        Plan target = new Plan();
        target.setId(30L);
        target.setUserId(1L);
        target.setPlanDays(3);
        when(planDayRepository.findById(20L)).thenReturn(Optional.of(source));
        when(planAccessService.requireEditorOwnerId(1L, 10L)).thenReturn(1L);
        when(planAccessService.requireEditor(1L, 30L)).thenReturn(target);
        when(planDayRepository.existsByUserIdAndPlanIdAndDayOrder(1L, 30L, 2)).thenReturn(false);
        when(planDayRepository.save(any(PlanDay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L)).thenReturn(List.of(original));

        service.copyPlanDay(1L, 20L, new PlanDayCopyRequest(30L, 2, "2일차 복사"));

        ArgumentCaptor<DaySchedule> captor = ArgumentCaptor.forClass(DaySchedule.class);
        verify(dayScheduleRepository).save(captor.capture());
        DaySchedule copied = captor.getValue();
        assertThat(copied.getPlanDay().getPlan()).isEqualTo(target);
        assertThat(copied.getPlanDay().getDayOrder()).isEqualTo(2);
        assertThat(copied.getSpotNameSnapshot()).isEqualTo("청수사");
        assertThat(copied.getIsChecked()).isFalse();
        assertThat(copied.getIsSkipped()).isFalse();
    }

    @Test
    void searchesOnlyOwnedIndependentDaysUsingTrimmedTitleAndPreservesPaging() {
        var pageable = org.springframework.data.domain.PageRequest.of(2, 9);
        PlanDay found = day(12L, null, 1);
        found.setDayName("교토 산책");
        when(planDayRepository.findAllByUserIdAndPlanIsNullAndDayNameContainingIgnoreCase(7L, "교토", pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(found), pageable, 19));
        var result = service.getAllIndependentDay(7L, pageable, "  교토  ");
        assertThat(result.getContent()).extracting(study.yume.dto.planday.response.PlanDayResponse::dayName).containsExactly("교토 산책");
        assertThat(result.getTotalElements()).isEqualTo(19);
        assertThat(result.getNumber()).isEqualTo(2);
        verify(planDayRepository).findAllByUserIdAndPlanIsNullAndDayNameContainingIgnoreCase(7L, "교토", pageable);
    }

    @Test
    void blankOrMissingKeywordKeepsTheUnfilteredList() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 9);
        when(planDayRepository.findAllByUserIdAndPlanIsNull(7L, pageable)).thenReturn(org.springframework.data.domain.Page.empty(pageable));
        service.getAllIndependentDay(7L, pageable, null);
        service.getAllIndependentDay(7L, pageable, "   ");
        verify(planDayRepository, org.mockito.Mockito.times(2)).findAllByUserIdAndPlanIsNull(7L, pageable);
        verify(planDayRepository, org.mockito.Mockito.never()).findAllByUserIdAndPlanIsNullAndDayNameContainingIgnoreCase(any(), any(), any());
    }
}
