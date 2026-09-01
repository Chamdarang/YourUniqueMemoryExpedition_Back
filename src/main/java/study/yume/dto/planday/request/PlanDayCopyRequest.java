package study.yume.dto.planday.request;

public record PlanDayCopyRequest(
        Long targetPlanId,
        Integer targetDayOrder,
        String dayName
) {
}
