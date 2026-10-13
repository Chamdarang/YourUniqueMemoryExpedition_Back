package study.yume.dto.plan.transfer;

import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;
import study.yume.model.enums.ScheduleMode;
import study.yume.model.enums.PlanReservationStatus;
import study.yume.model.enums.PlanReservationType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanTransferDto(
        Integer formatVersion,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo,
        List<Day> days,
        List<Reservation> reservations
) {
    public record Reservation(
            PlanReservationType type,
            String title,
            PlanReservationStatus status,
            String provider,
            String confirmationCode,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String bookingUrl,
            String address,
            String memo,
            Integer dayOrder
    ) {}

    public record Day(
            String dayName,
            Integer dayOrder,
            String memo,
            ScheduleMode scheduleMode,
            List<Schedule> schedules
    ) {
    }

    public record Schedule(
            Integer scheduleOrder,
            Long spotUserId,
            String spotName,
            SpotType spotType,
            Double lat,
            Double lng,
            Boolean isChecked,
            Boolean isSkipped,
            LocalTime startTime,
            Boolean fixedStartTime,
            Integer duration,
            LocalTime endTime,
            Integer movingDuration,
            Integer extraDuration,
            Integer extraMovingDuration,
            Transportation transportation,
            String memo,
            String movingMemo
    ) {
    }
}
