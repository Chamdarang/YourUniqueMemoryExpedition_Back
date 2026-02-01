package study.yume.dto.plan.response;

import study.yume.dto.planday.response.PlanDayResponse;
import study.yume.model.Plan;

import java.time.LocalDate;
import java.util.List;

public record PlanDetailResponse(
        Long id,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo,
        List<PlanDayResponse> days
) {
    public static PlanDetailResponse toDto(Plan plan, List<PlanDayResponse> days) {
        return new PlanDetailResponse(
                plan.getId(),
                plan.getPlanName(),
                plan.getPlanStartDate(),
                plan.getPlanEndDate(),
                plan.getPlanDays(),
                plan.getPlanMemo(),
                days
        );
    }
}
