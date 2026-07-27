package study.yume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.schedule.request.ScheduleUpdateRequest;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.SpotUser;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayScheduleServiceTests {

    @Mock
    private DayScheduleRepository dayScheduleRepository;
    @Mock
    private PlanDayRepository planDayRepository;
    @Mock
    private SpotUserRepository spotUserRepository;
    @Mock
    private SpotVisitHistoryRepository spotVisitHistoryRepository;

    private DayScheduleService service;

    @BeforeEach
    void setUp() {
        service = new DayScheduleService(
                dayScheduleRepository,
                planDayRepository,
                spotUserRepository,
                spotVisitHistoryRepository
        );
    }

    @Test
    void clearsSpotUserWhenZeroIsExplicitlyRequested() {
        DaySchedule schedule = schedule(10L, LocalTime.of(9, 0), false);
        schedule.setSpotUser(new SpotUser());
        when(dayScheduleRepository.findByUserIdAndId(1L, 10L)).thenReturn(Optional.of(schedule));
        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(List.of(schedule));

        service.updateSchedule(1L, 10L, updateRequest(0L, null, null));

        assertThat(schedule.getSpotUser()).isNull();
        verify(spotUserRepository, never()).findByUserIdAndId(1L, 0L);
    }

    @Test
    void preservesFixedStartAndContinuesFollowingSchedulesFromIt() {
        DaySchedule first = schedule(10L, LocalTime.of(9, 0), false);
        first.setDuration(60);
        DaySchedule fixed = schedule(11L, LocalTime.of(13, 0), true);
        fixed.setDuration(30);
        fixed.setMovingDuration(10);
        DaySchedule following = schedule(12L, LocalTime.of(0, 0), false);
        following.setDuration(20);
        following.setMovingDuration(15);

        when(dayScheduleRepository.findByUserIdAndId(1L, 11L)).thenReturn(Optional.of(fixed));
        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(List.of(first, fixed, following));

        service.updateSchedule(1L, 11L, updateRequest(null, LocalTime.of(13, 0), true));

        assertThat(fixed.getStartTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(fixed.getEndTime()).isEqualTo(LocalTime.of(13, 30));
        assertThat(following.getStartTime()).isEqualTo(LocalTime.of(13, 45));
    }

    private DaySchedule schedule(Long id, LocalTime startTime, boolean fixedStartTime) {
        PlanDay day = new PlanDay();
        day.setId(20L);

        DaySchedule schedule = new DaySchedule();
        schedule.setId(id);
        schedule.setUserId(1L);
        schedule.setPlanDay(day);
        schedule.setStartTime(startTime);
        schedule.setFixedStartTime(fixedStartTime);
        schedule.setDuration(60);
        schedule.setEndTime(startTime.plusHours(1));
        return schedule;
    }

    private ScheduleUpdateRequest updateRequest(
            Long spotUserId,
            LocalTime startTime,
            Boolean fixedStartTime
    ) {
        return new ScheduleUpdateRequest(
                spotUserId,
                null,
                null,
                null,
                null,
                startTime,
                fixedStartTime,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
