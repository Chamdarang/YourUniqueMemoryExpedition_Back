package study.yume.dto.plan.response;

import study.yume.dto.plan.transfer.PlanTransferDto;

import java.util.List;

public record PlanImportPreviewResponse(
        PlanTransferDto plan,
        Summary summary,
        List<Issue> issues
) {
    public record Summary(
            int sourceRows,
            int importedDays,
            int importedSchedules,
            int skippedRows,
            int fixedStartTimes,
            int newSpots
    ) {
    }

    public record Issue(
            int rowNumber,
            String severity,
            String message,
            String value
    ) {
    }

}
