package study.yume.service;

import study.yume.model.enums.Transportation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

final class PlanImportModels {
    private PlanImportModels() {
    }

    record DayRows(String label, LocalDate date, List<RawEntry> rows, List<String> memos) {
    }

    record RawEntry(
            int rowNumber,
            String place,
            boolean movement,
            String destination,
            LocalTime start,
            Integer duration,
            LocalTime end,
            int offset,
            Transportation transportation,
            String memo,
            String movingMemo
    ) {
    }
}
