package study.yume.dto.plan.request;

import java.time.LocalDate;

public record PlanCreateRequest(
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo
) {
}
