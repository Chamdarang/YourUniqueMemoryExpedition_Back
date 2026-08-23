package study.yume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.dto.schedule.request.ScheduleCreateRequest;
import study.yume.dto.schedule.request.ScheduleUpdateRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.model.DaySchedule;
import study.yume.model.PlanDay;
import study.yume.model.Spot;
import study.yume.model.SpotUser;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;

import java.time.LocalTime;
import java.util.ArrayList;
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

    @Test
    void createsQuickScheduleWithLinkedSpotFixedTimeAndMemo() {
        PlanDay day = new PlanDay();
        day.setId(20L);

        Spot spot = new Spot();
        spot.setSpotName("원래 장소명");
        spot.setLocation(new GeometryFactory().createPoint(new Coordinate(135.5, 34.7)));

        SpotUser spotUser = new SpotUser();
        spotUser.setId(30L);
        spotUser.setSpot(spot);
        spotUser.setCustomName("표시 장소명");
        spotUser.setSpotType(SpotType.LANDMARK);

        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(new ArrayList<>());
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(day));
        when(spotUserRepository.findByUserIdAndId(1L, 30L)).thenReturn(Optional.of(spotUser));

        List<DayScheduleResponse> result = service.createSchedule(
                1L,
                20L,
                new ScheduleCreateRequest(0, 30L, "무시할 이름", null, null, null, LocalTime.of(13, 30), "예약 확인")
        );

        DayScheduleResponse created = result.get(0);
        assertThat(created.spotUserId()).isEqualTo(30L);
        assertThat(created.spotName()).isEqualTo("표시 장소명");
        assertThat(created.startTime()).isEqualTo(LocalTime.of(13, 30));
        assertThat(created.fixedStartTime()).isTrue();
        assertThat(created.duration()).isEqualTo(60);
        assertThat(created.memo()).isEqualTo("예약 확인");
        assertThat(created.lat()).isEqualTo(34.7);
        assertThat(created.lng()).isEqualTo(135.5);
    }

    @Test
    void createsQuickScheduleWithoutTimeAfterPreviousSchedule() {
        PlanDay day = new PlanDay();
        day.setId(20L);
        DaySchedule first = schedule(10L, LocalTime.of(9, 0), false);

        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(new ArrayList<>(List.of(first)));
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(day));

        List<DayScheduleResponse> result = service.createSchedule(
                1L,
                20L,
                new ScheduleCreateRequest(1, null, "직접 입력 장소", null, null, null, null, null)
        );

        DayScheduleResponse created = result.get(1);
        assertThat(created.spotUserId()).isNull();
        assertThat(created.spotName()).isEqualTo("직접 입력 장소");
        assertThat(created.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(created.fixedStartTime()).isFalse();
    }

    @Test
    void createsQuickScheduleWithMapCoordinatesWithoutRegisteringSpot() {
        PlanDay day = new PlanDay();
        day.setId(20L);

        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(new ArrayList<>());
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(day));

        List<DayScheduleResponse> result = service.createSchedule(
                1L,
                20L,
                new ScheduleCreateRequest(0, null, "지도에서 선택", 35.0, 135.0, SpotType.OTHER, null, null)
        );

        DayScheduleResponse created = result.get(0);
        assertThat(created.spotUserId()).isNull();
        assertThat(created.spotName()).isEqualTo("지도에서 선택");
        assertThat(created.lat()).isEqualTo(35.0);
        assertThat(created.lng()).isEqualTo(135.0);
        assertThat(created.spotType()).isEqualTo(SpotType.OTHER);
    }

    @Test
    void adjustsPreviousDurationToNextQuickScheduleStartTime() {
        PlanDay day = new PlanDay();
        day.setId(20L);
        day.setScheduleMode(ScheduleMode.SIMPLE);
        DaySchedule first = schedule(10L, LocalTime.of(9, 0), true);

        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(new ArrayList<>(List.of(first)));
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(day));

        List<DayScheduleResponse> result = service.createSchedule(
                1L,
                20L,
                new ScheduleCreateRequest(1, null, "다음 장소", null, null, null, LocalTime.of(11, 30), null)
        );

        assertThat(result.get(0).duration()).isEqualTo(150);
        assertThat(result.get(0).endTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    void keepsQuickScheduleTimeEmptyInSimpleMode() {
        PlanDay day = new PlanDay();
        day.setId(20L);
        day.setScheduleMode(ScheduleMode.SIMPLE);

        when(dayScheduleRepository.findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(1L, 20L))
                .thenReturn(new ArrayList<>());
        when(planDayRepository.findByUserIdAndId(1L, 20L)).thenReturn(Optional.of(day));

        List<DayScheduleResponse> result = service.createSchedule(
                1L,
                20L,
                new ScheduleCreateRequest(0, null, "시간 없는 장소", null, null, null, null, null)
        );

        assertThat(result.get(0).startTime()).isNull();
        assertThat(result.get(0).endTime()).isNull();
        assertThat(result.get(0).fixedStartTime()).isFalse();
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
