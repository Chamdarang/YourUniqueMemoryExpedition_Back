package study.yume.dto.planday.request;

import study.yume.model.enums.SwapMode;

public record PlanDaySwapRequest(
        Long sourceDayId,
        Long targetPlanId,
        Integer targetDayOrder,
        SwapMode swapMode
) {
}
