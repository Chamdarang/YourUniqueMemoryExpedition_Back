package study.yume.dto.plan.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import study.yume.model.enums.Transportation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record GeneralImportConfig(
        String planName,
        LocalDate startDate,
        List<String> sheetNames,
        Integer headerRow,
        Integer dataStartRow,
        String dayMode,
        String rowMode,
        Map<String, Integer> columns,
        List<String> movementTypeValues,
        String durationUnit,
        String movingDurationUnit,
        @JsonFormat(pattern = "HH:mm") LocalTime defaultStartTime,
        Integer defaultDurationMinutes,
        Integer lastDurationMinutes,
        Boolean firstLineAsPlaceName,
        Boolean inheritBlankDay,
        Map<String, Transportation> transportationMappings,
        String csvCharset,
        String csvDelimiter
) {
}
