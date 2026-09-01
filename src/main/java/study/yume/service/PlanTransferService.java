package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.plan.response.PlanResponse;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.SpotUser;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.SpotUserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanTransferService {

    private static final int FORMAT_VERSION = 1;

    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final SpotUserRepository spotUserRepository;
    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional(readOnly = true)
    public PlanTransferDto exportPlan(Long userId, Long planId) {
        Plan plan = planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));

        List<PlanTransferDto.Day> days = planDayRepository
                .findAllByUserIdAndPlanIdOrderByDayOrderAsc(userId, planId)
                .stream()
                .map(day -> new PlanTransferDto.Day(
                        day.getDayName(),
                        day.getDayOrder(),
                        day.getMemo(),
                        day.getScheduleMode(),
                        dayScheduleRepository
                                .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(userId, day.getId())
                                .stream()
                                .map(this::toTransferSchedule)
                                .toList()
                ))
                .toList();

        return new PlanTransferDto(
                FORMAT_VERSION,
                plan.getPlanName(),
                plan.getPlanStartDate(),
                plan.getPlanEndDate(),
                plan.getPlanDays(),
                plan.getPlanMemo(),
                days
        );
    }

    public PlanResponse importPlan(Long userId, PlanTransferDto transfer) {
        validate(transfer);

        List<PlanTransferDto.Day> days = transfer.days() == null
                ? List.of()
                : transfer.days().stream()
                    .sorted(Comparator.comparing(PlanTransferDto.Day::dayOrder))
                    .toList();

        Integer planDays = resolvePlanDays(transfer, days);
        LocalDate startDate = transfer.planStartDate();
        LocalDate endDate = transfer.planEndDate();
        if (startDate != null && endDate == null && planDays != null) {
            endDate = startDate.plusDays(planDays - 1L);
        } else if (endDate != null && startDate == null && planDays != null) {
            startDate = endDate.minusDays(planDays - 1L);
        }

        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setPlanName(transfer.planName().trim());
        plan.setPlanStartDate(startDate);
        plan.setPlanEndDate(endDate);
        plan.setPlanDays(planDays);
        plan.setPlanMemo(transfer.planMemo());
        plan = planRepository.save(plan);

        for (PlanTransferDto.Day importedDay : days) {
            PlanDay day = new PlanDay();
            day.setUserId(userId);
            day.setPlan(plan);
            day.setDayName(importedDay.dayName());
            day.setDayOrder(importedDay.dayOrder());
            day.setMemo(importedDay.memo());
            day.setScheduleMode(importedDay.scheduleMode() == null ? ScheduleMode.DETAILED : importedDay.scheduleMode());
            day = planDayRepository.save(day);

            List<PlanTransferDto.Schedule> schedules = importedDay.schedules() == null
                    ? List.of()
                    : importedDay.schedules().stream()
                        .sorted(Comparator.comparing(
                                schedule -> schedule.scheduleOrder() == null
                                        ? Integer.MAX_VALUE
                                        : schedule.scheduleOrder()
                        ))
                        .toList();

            for (int index = 0; index < schedules.size(); index++) {
                dayScheduleRepository.save(toEntity(userId, day, schedules.get(index), index));
            }
        }

        return PlanResponse.toDto(plan);
    }

    private PlanTransferDto.Schedule toTransferSchedule(DaySchedule schedule) {
        return new PlanTransferDto.Schedule(
                schedule.getScheduleOrder(),
                schedule.getSpotUser() == null ? null : schedule.getSpotUser().getId(),
                schedule.getSpotNameSnapshot(),
                schedule.getSpotTypeSnapshot(),
                schedule.getSpotLocationSnapshot() == null ? null : schedule.getSpotLocationSnapshot().getY(),
                schedule.getSpotLocationSnapshot() == null ? null : schedule.getSpotLocationSnapshot().getX(),
                schedule.getIsChecked(),
                schedule.getIsSkipped(),
                schedule.getStartTime(),
                schedule.isFixedStartTime(),
                schedule.getDuration(),
                schedule.getEndTime(),
                schedule.getMovingDuration(),
                schedule.getExtraDuration(),
                schedule.getExtraMovingDuration(),
                schedule.getTransportation(),
                schedule.getMemo(),
                schedule.getMovingMemo()
        );
    }

    private DaySchedule toEntity(
            Long userId,
            PlanDay day,
            PlanTransferDto.Schedule imported,
            int normalizedOrder
    ) {
        DaySchedule schedule = new DaySchedule();
        schedule.setUserId(userId);
        schedule.setPlanDay(day);
        schedule.setScheduleOrder(normalizedOrder);
        SpotUser linkedSpot = imported.spotUserId() == null
                ? null
                : spotUserRepository.findByUserIdAndId(userId, imported.spotUserId()).orElse(null);
        schedule.setSpotUser(linkedSpot);
        schedule.setSpotNameSnapshot(imported.spotName() != null
                ? imported.spotName()
                : linkedSpot == null ? null : linkedSpot.getSpot().getSpotName());
        if (imported.lat() != null && imported.lng() != null) {
            schedule.setSpotLocationSnapshot(
                    geometryFactory.createPoint(new Coordinate(imported.lng(), imported.lat()))
            );
        } else if (linkedSpot != null) {
            schedule.setSpotLocationSnapshot(linkedSpot.getSpot().getLocation());
        }
        schedule.setSpotTypeSnapshot(imported.spotType() != null
                ? imported.spotType()
                : linkedSpot == null ? null : linkedSpot.getSpotType());
        schedule.setIsChecked(Boolean.TRUE.equals(imported.isChecked()));
        schedule.setIsSkipped(Boolean.TRUE.equals(imported.isSkipped()));
        schedule.setStartTime(imported.startTime());
        schedule.setFixedStartTime(Boolean.TRUE.equals(imported.fixedStartTime()));
        schedule.setDuration(imported.duration());
        schedule.setEndTime(imported.endTime() != null
                ? imported.endTime()
                : imported.startTime() == null ? null : imported.startTime().plusMinutes(imported.duration()));
        schedule.setMovingDuration(valueOrZero(imported.movingDuration()));
        schedule.setExtraDuration(valueOrZero(imported.extraDuration()));
        schedule.setExtraMovingDuration(valueOrZero(imported.extraMovingDuration()));
        schedule.setTransportation(imported.transportation());
        schedule.setMemo(imported.memo());
        schedule.setMovingMemo(imported.movingMemo());
        return schedule;
    }

    private void validate(PlanTransferDto transfer) {
        if (transfer == null) {
            throw new IllegalArgumentException("불러올 계획 데이터가 없습니다.");
        }
        if (!Integer.valueOf(FORMAT_VERSION).equals(transfer.formatVersion())) {
            throw new IllegalArgumentException("지원하지 않는 계획 파일 버전입니다.");
        }
        requireText(transfer.planName(), 200, "계획 이름");
        requireMaxLength(transfer.planMemo(), 500, "계획 메모");

        if (transfer.planStartDate() != null && transfer.planEndDate() != null) {
            if (transfer.planStartDate().isAfter(transfer.planEndDate())) {
                throw new IllegalArgumentException("계획 시작일은 종료일보다 늦을 수 없습니다.");
            }
            if (transfer.planDays() != null) {
                long dateDays = ChronoUnit.DAYS.between(
                        transfer.planStartDate(),
                        transfer.planEndDate()
                ) + 1;
                if (dateDays != transfer.planDays()) {
                    throw new IllegalArgumentException("계획 날짜와 여행 일수가 일치하지 않습니다.");
                }
            }
        }
        if (transfer.planDays() != null && transfer.planDays() < 1) {
            throw new IllegalArgumentException("여행 일수는 1일 이상이어야 합니다.");
        }

        Set<Integer> dayOrders = new HashSet<>();
        for (PlanTransferDto.Day day : transfer.days() == null ? List.<PlanTransferDto.Day>of() : transfer.days()) {
            if (day.dayOrder() == null || day.dayOrder() < 1 || !dayOrders.add(day.dayOrder())) {
                throw new IllegalArgumentException("일차 순서가 올바르지 않거나 중복되었습니다.");
            }
            requireText(day.dayName(), 255, "일차 이름");
            requireMaxLength(day.memo(), 500, "일차 메모");

            for (PlanTransferDto.Schedule schedule
                    : day.schedules() == null ? List.<PlanTransferDto.Schedule>of() : day.schedules()) {
                if (schedule.startTime() == null && day.scheduleMode() != ScheduleMode.SIMPLE) {
                    throw new IllegalArgumentException("일정 시작 시간이 필요합니다.");
                }
                if (schedule.duration() == null || schedule.duration() < 0
                        || valueOrZero(schedule.movingDuration()) < 0
                        || valueOrZero(schedule.extraDuration()) < 0
                        || valueOrZero(schedule.extraMovingDuration()) < 0) {
                    throw new IllegalArgumentException("일정 소요시간은 0 이상이어야 합니다.");
                }
                if ((schedule.lat() == null) != (schedule.lng() == null)) {
                    throw new IllegalArgumentException("장소 위도와 경도는 함께 입력되어야 합니다.");
                }
                if (schedule.lat() != null
                        && (!Double.isFinite(schedule.lat())
                        || !Double.isFinite(schedule.lng())
                        || Math.abs(schedule.lat()) > 90
                        || Math.abs(schedule.lng()) > 180)) {
                    throw new IllegalArgumentException("장소 좌표가 올바르지 않습니다.");
                }
                requireMaxLength(schedule.spotName(), 200, "장소 이름");
                requireMaxLength(schedule.memo(), 500, "일정 메모");
                requireMaxLength(schedule.movingMemo(), 500, "이동 메모");
            }
        }
    }

    private Integer resolvePlanDays(PlanTransferDto transfer, List<PlanTransferDto.Day> days) {
        int largestDayOrder = days.stream()
                .map(PlanTransferDto.Day::dayOrder)
                .max(Integer::compareTo)
                .orElse(0);
        Integer planDays = transfer.planDays();
        if (planDays == null && transfer.planStartDate() != null && transfer.planEndDate() != null) {
            planDays = (int) ChronoUnit.DAYS.between(
                    transfer.planStartDate(),
                    transfer.planEndDate()
            ) + 1;
        }
        if (planDays == null && largestDayOrder > 0) {
            planDays = largestDayOrder;
        }
        if (planDays != null && largestDayOrder > planDays) {
            throw new IllegalArgumentException("계획 일수보다 큰 일차가 포함되어 있습니다.");
        }
        return planDays;
    }

    private void requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "이(가) 필요합니다.");
        }
        requireMaxLength(value, maxLength, fieldName);
    }

    private void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
