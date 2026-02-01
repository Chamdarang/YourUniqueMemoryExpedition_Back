package study.yume.dto.planday.response;

import study.yume.model.PlanDay;


public record PlanDayResponse(
        Long id,
        String dayName,
        Integer dayOrder
) {
    public static PlanDayResponse toDto(PlanDay planDay) {
        return new PlanDayResponse(
                planDay.getId(),
                planDay.getDayName(),
                planDay.getDayOrder()
        );
    }
}
