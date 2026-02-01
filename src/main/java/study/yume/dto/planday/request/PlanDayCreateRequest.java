package study.yume.dto.planday.request;

public record PlanDayCreateRequest(
        String dayName,
        Integer dayOrder
) {
}
