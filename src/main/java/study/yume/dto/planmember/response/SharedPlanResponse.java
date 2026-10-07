package study.yume.dto.planmember.response;

import java.time.LocalDate;

public record SharedPlanResponse(
        Long id,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo,
        String ownerNickname
) {
}
