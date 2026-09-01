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
    @Mock private PlanDayRepository planDayRepository;
    @Mock private PlanRepository planRepository;
    @Mock private DayScheduleRepository dayScheduleRepository;

    private PlanDayService service;

    @BeforeEach
    void setUp() {
        service = new PlanDayService(planDayRepository, planRepository, dayScheduleRepository);
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
        target.setPlanDays(3);
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(source));
        when(planRepository.findByUserIdAndId(1L, 30L)).thenReturn(Optional.of(target));
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
}
