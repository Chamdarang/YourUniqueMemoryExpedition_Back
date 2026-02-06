package study.yume.dto.spotvisithistory.response;

import study.yume.model.SpotVisitHistory;

import java.time.LocalDate;

public record SpotVisitHistoryResponse(
        Long id,
        Long planId,
        String planName,
        Long dayId,
        String dayName,
        LocalDate visitedAt
) {
    public static SpotVisitHistoryResponse toDto(SpotVisitHistory history) {
        return new SpotVisitHistoryResponse(
                history.getId(),
                history.getPlanId(),
                history.getPlanNameSnapshot(),
                history.getDayId(),
                history.getDayNameSnapshot(),
                history.getVisitedAt()
        );
    }
}