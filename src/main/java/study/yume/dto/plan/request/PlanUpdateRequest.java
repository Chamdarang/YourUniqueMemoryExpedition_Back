package study.yume.dto.plan.request;

import java.time.LocalDate;
import java.util.List;

public record PlanUpdateRequest(
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo,
        Long expectedVersion,
        List<Long> detachDayIds
) {
}
