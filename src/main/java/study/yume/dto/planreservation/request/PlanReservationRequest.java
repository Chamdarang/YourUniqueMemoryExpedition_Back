package study.yume.dto.planreservation.request;

import study.yume.model.enums.PlanReservationStatus;
import study.yume.model.enums.PlanReservationType;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlanReservationRequest(
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
        Long dayId
) {}
