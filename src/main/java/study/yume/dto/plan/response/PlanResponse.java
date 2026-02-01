package study.yume.dto.plan.response;

import study.yume.model.Plan;

import java.time.LocalDate;

public record PlanResponse(
        Long id,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo
) {
    public static PlanResponse toDto(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getPlanName(),
                plan.getPlanStartDate(),
                plan.getPlanEndDate(),
                plan.getPlanDays(),
                plan.getPlanMemo()
        );
    }
}
