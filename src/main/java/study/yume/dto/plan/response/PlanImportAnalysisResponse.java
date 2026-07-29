package study.yume.dto.plan.response;

import java.util.List;

public record PlanImportAnalysisResponse(
        String fileName,
        String fileType,
        String detectedCharset,
        String detectedDelimiter,
        List<Sheet> sheets
) {
    public record Sheet(
            String name,
            int rowCount,
            int suggestedHeaderRow,
            List<Column> columns
    ) {
    }

    public record Column(
            int index,
            String label,
            List<String> samples
    ) {
    }
}
