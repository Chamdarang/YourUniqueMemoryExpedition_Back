package study.yume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.model.*;
import study.yume.repository.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleVisitTests {
    @Mock DayScheduleRepository schedules;
    @Mock PlanDayRepository days;
    @Mock SpotUserRepository spots;
    @Mock SpotVisitHistoryRepository histories;
    @Mock ScheduleVisitStatusRepository statusesRepository;
    @Mock PlanAccessService planAccessService;
    @Mock PlanLiveUpdateService planLiveUpdateService;
    DayScheduleService service;
    List<SpotVisitHistory> saved;
    Map<Long, ScheduleVisitStatus> statuses;
    SpotUser personalSpot;

    @BeforeEach
    void setUp() {
        service = new DayScheduleService(schedules, days, spots, histories, statusesRepository, planAccessService, planLiveUpdateService);
        saved = new ArrayList<>();
        statuses = new HashMap<>();
        Spot spot = new Spot();
        spot.setId(70L);
        personalSpot = new SpotUser();
        personalSpot.setId(40L);
        personalSpot.setSpot(spot);
        personalSpot.setIsVisit(false);
        when(statusesRepository.findByUserIdAndScheduleId(anyLong(), anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(statuses.get(invocation.getArgument(1)))
        );
        when(statusesRepository.save(any())).thenAnswer(invocation -> {
            ScheduleVisitStatus status = invocation.getArgument(0);
            statuses.put(status.getScheduleId(), status);
            return status;
        });
        when(spots.findByUserIdAndSpotId(1L, 70L)).thenReturn(Optional.of(personalSpot));
        when(histories.save(any())).thenAnswer(invocation -> {
            SpotVisitHistory history = invocation.getArgument(0);
            saved.add(history);
            return history;
        });
    }

    @Test
    void recordsHistoricalTripDateAndScheduleId() {
        DaySchedule schedule = schedule(10L);
        Plan plan = new Plan();
        plan.setId(30L);
        plan.setPlanName("지난 여행");
        plan.setPlanStartDate(LocalDate.of(2024, 12, 31));
        schedule.getPlanDay().setPlan(plan);
        schedule.getPlanDay().setDayOrder(2);
        when(planAccessService.requireEditorOwnerId(1L, 30L)).thenReturn(1L);

        service.updateVisit(1L, 10L);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getVisitedAt()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(saved.get(0).getScheduleId()).isEqualTo(10L);
        assertThat(saved.get(0).getPlanId()).isEqualTo(30L);
        assertThat(schedule.getSpotUser().getIsVisit()).isTrue();
    }

    @Test
    void usesTodayForUndatedIndependentDay() {
        schedule(10L);
        LocalDate before = LocalDate.now();
        service.updateVisit(1L, 10L);
        assertThat(saved.get(0).getVisitedAt()).isBetween(before, LocalDate.now());
    }

    @Test
    void cancellingOneOfTwoVisitsKeepsTheOther() {
        verifySeparateVisits(false);
    }

    @Test
    void skippingOneOfTwoVisitsKeepsTheOther() {
        verifySeparateVisits(true);
    }

    private void verifySeparateVisits(boolean skip) {
        DaySchedule first = schedule(10L);
        DaySchedule second = schedule(11L);
        second.setSpotUser(first.getSpotUser());
        second.setPlanDay(first.getPlanDay());
        doAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            Long scheduleId = invocation.getArgument(1);
            saved.removeIf(h -> h.getUserId().equals(userId) && h.getScheduleId().equals(scheduleId));
            return null;
        }).when(histories).deleteByUserIdAndScheduleId(anyLong(), anyLong());
        service.updateVisit(1L, 10L);
        service.updateVisit(1L, 11L);

        if (skip) service.updateSkip(1L, 10L);
        else service.updateVisit(1L, 10L);

        assertThat(saved).extracting(SpotVisitHistory::getScheduleId).containsExactly(11L);
        assertThat(statuses.get(10L).isChecked()).isFalse();
        assertThat(statuses.get(11L).isChecked()).isTrue();
        assertThat(statuses.get(10L).isSkipped()).isEqualTo(skip);
    }

    private DaySchedule schedule(Long id) {
        PlanDay day = new PlanDay();
        day.setId(20L);
        day.setUserId(1L);
        day.setDayName("방문 일차");
        day.setDayOrder(1);
        DaySchedule schedule = new DaySchedule();
        schedule.setId(id);
        schedule.setUserId(1L);
        schedule.setPlanDay(day);
        schedule.setSpotUser(personalSpot);
        schedule.setIsChecked(false);
        when(schedules.findById(id)).thenReturn(Optional.of(schedule));
        return schedule;
    }
}
