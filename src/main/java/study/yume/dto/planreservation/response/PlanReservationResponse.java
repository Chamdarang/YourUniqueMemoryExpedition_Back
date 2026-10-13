package study.yume.dto.planreservation.response;

import study.yume.model.PlanReservation;
import study.yume.model.enums.PlanReservationStatus;
import study.yume.model.enums.PlanReservationType;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlanReservationResponse(
        Long id,
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
        Long dayId,
        Integer dayOrder
) {
    public static PlanReservationResponse from(PlanReservation reservation) {
        boolean linkedDayInPlan = reservation.getPlanDay() != null
                && reservation.getPlanDay().getPlan() != null
                && reservation.getPlanDay().getPlan().getId().equals(reservation.getPlan().getId());
        return new PlanReservationResponse(
                reservation.getId(), reservation.getType(), reservation.getTitle(), reservation.getStatus(),
                reservation.getProvider(), reservation.getConfirmationCode(), reservation.getStartDate(),
                reservation.getEndDate(), reservation.getStartTime(), reservation.getEndTime(),
                reservation.getBookingUrl(), reservation.getAddress(), reservation.getMemo(),
                linkedDayInPlan ? reservation.getPlanDay().getId() : null,
                linkedDayInPlan ? reservation.getPlanDay().getDayOrder() : null
        );
    }
}
