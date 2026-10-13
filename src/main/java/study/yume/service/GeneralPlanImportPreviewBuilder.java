package study.yume.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.dto.plan.response.PlanImportPreviewResponse;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.ScheduleMode;
import study.yume.service.PlanImportModels.DayRows;
import study.yume.service.PlanImportModels.RawEntry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GeneralPlanImportPreviewBuilder {

    private final PlanImportTableReader tableReader;
    private final PlanImportEntryParser entryParser;
    private final PlanImportScheduleBuilder scheduleBuilder;

    public GeneralPlanImportPreviewBuilder(
            PlanImportTableReader tableReader,
            PlanImportEntryParser entryParser,
            PlanImportScheduleBuilder scheduleBuilder
    ) {
        this.tableReader = tableReader;
        this.entryParser = entryParser;
        this.scheduleBuilder = scheduleBuilder;
    }

    public PlanImportPreviewResponse preview(MultipartFile file, GeneralImportConfig config) {
        validateConfig(config);
        PlanImportTableReader.TableFile table = tableReader.read(file, config.csvCharset(), config.csvDelimiter());
        Set<String> selectedSheets = config.sheetNames() == null
                ? Set.of()
                : new LinkedHashSet<>(config.sheetNames());
        List<PlanImportTableReader.TableSheet> sheets = table.sheets().stream()
                .filter(sheet -> selectedSheets.isEmpty() || selectedSheets.contains(sheet.name()))
                .toList();
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("가져올 시트를 하나 이상 선택해 주세요.");
        }

        PlanImportIssueCollector issues = new PlanImportIssueCollector();
        Map<String, DayRows> grouped = new LinkedHashMap<>();
        int sourceRows = 0;
        int dataStartIndex = Math.max(
                0,
                valueOr(config.dataStartRow(), valueOr(config.headerRow(), 1) + 1) - 1
        );
        String inheritedDay = "";

        for (PlanImportTableReader.TableSheet sheet : sheets) {
            if ("SHEET".equals(normalizeMode(config.dayMode()))) {
                inheritedDay = sheet.name();
            }
            for (int rowIndex = dataStartIndex; rowIndex < sheet.rows().size(); rowIndex++) {
                List<String> row = sheet.rows().get(rowIndex);
                if (!hasMappedContent(row, config)) continue;
                sourceRows++;

                String place = mapped(row, config, "place");
                String dayMemo = mapped(row, config, "dayMemo");
                String dayValue = switch (normalizeMode(config.dayMode())) {
                    case "SHEET" -> sheet.name();
                    case "DATE" -> mapped(row, config, "date");
                    case "COLUMN" -> mapped(row, config, "day");
                    default -> "1일차";
                };
                if (dayValue.isBlank() && Boolean.TRUE.equals(config.inheritBlankDay())) {
                    dayValue = inheritedDay;
                }
                if (!dayValue.isBlank()) inheritedDay = dayValue;
                if (dayValue.isBlank()) {
                    issues.addRowIssue(rowIndex, "ERROR", "일차 또는 날짜를 확인할 수 없어 제외했습니다.", place);
                    continue;
                }
                String dayKey = normalizeDayKey(dayValue, config.dayMode(), issues, rowIndex);
                if (dayKey == null) continue;
                String resolvedDayValue = dayValue;
                DayRows day = grouped.computeIfAbsent(dayKey, ignored ->
                        new DayRows(resolvedDayValue, entryParser.parseDate(resolvedDayValue), new ArrayList<>(), new ArrayList<>()));
                if (!dayMemo.isBlank() && !day.memos().contains(dayMemo.trim())) {
                    day.memos().add(dayMemo.trim());
                }
                if (place.isBlank()) {
                    if (hasScheduleContent(row, config)) {
                        issues.addRowIssue(rowIndex, "ERROR", "장소명 또는 목적이 비어 있어 제외했습니다.", "");
                    }
                    continue;
                }

                RawEntry entry = entryParser.parseEntry(row, rowIndex, place, config, issues);
                if (entry != null) {
                    day.rows().add(entry);
                }
            }
        }
        if (grouped.isEmpty()) {
            throw new IllegalArgumentException("가져올 수 있는 일정 행을 찾지 못했습니다.");
        }

        List<DayRows> orderedDays = orderDays(grouped, config.dayMode());
        LocalDate resolvedStartDate = resolveStartDate(config, orderedDays);
        List<PlanTransferDto.Day> days = new ArrayList<>();
        int fixedStartTimes = 0;
        Set<String> uniquePlaces = new LinkedHashSet<>();

        for (int index = 0; index < orderedDays.size(); index++) {
            DayRows rawDay = orderedDays.get(index);
            List<PlanTransferDto.Schedule> schedules = scheduleBuilder.build(rawDay, config, issues);
            fixedStartTimes += (int) schedules.stream()
                    .filter(schedule -> Boolean.TRUE.equals(schedule.fixedStartTime()))
                    .count();
            schedules.stream()
                    .map(PlanTransferDto.Schedule::spotName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(entryParser::normalizeText)
                    .forEach(uniquePlaces::add);
            days.add(new PlanTransferDto.Day(
                    (index + 1) + "일차" + (rawDay.label().isBlank() ? "" : " (" + rawDay.label() + ")"),
                    index + 1,
                    String.join("\n\n", rawDay.memos()),
                    ScheduleMode.DETAILED,
                    schedules
            ));
        }

        int importedSchedules = days.stream().mapToInt(day -> day.schedules().size()).sum();
        if (importedSchedules == 0) {
            throw new IllegalArgumentException("가져올 수 있는 일정이 없습니다.");
        }
        PlanTransferDto plan = new PlanTransferDto(
                1,
                config.planName().trim(),
                resolvedStartDate,
                resolvedStartDate.plusDays(days.size() - 1L),
                days.size(),
                "범용 파일 Import: " + safeFileName(file.getOriginalFilename()),
                days,
                List.of()
        );
        return new PlanImportPreviewResponse(
                plan,
                new PlanImportPreviewResponse.Summary(
                        sourceRows,
                        days.size(),
                        importedSchedules,
                        issues.skippedRows(),
                        fixedStartTimes,
                        uniquePlaces.size()
                ),
                issues.all()
        );
    }

    private List<DayRows> orderDays(Map<String, DayRows> grouped, String dayMode) {
        if (!"DATE".equals(normalizeMode(dayMode))) return new ArrayList<>(grouped.values());
        List<DayRows> dated = grouped.values().stream()
                .filter(day -> day.date() != null)
                .sorted(Comparator.comparing(DayRows::date))
                .toList();
        if (dated.isEmpty()) return new ArrayList<>(grouped.values());
        Map<LocalDate, DayRows> byDate = new LinkedHashMap<>();
        dated.forEach(day -> byDate.put(day.date(), day));
        List<DayRows> result = new ArrayList<>();
        LocalDate current = dated.get(0).date();
        LocalDate last = dated.get(dated.size() - 1).date();
        while (!current.isAfter(last)) {
            result.add(byDate.getOrDefault(current, new DayRows(current.toString(), current, new ArrayList<>(), new ArrayList<>())));
            current = current.plusDays(1);
        }
        return result;
    }

    private LocalDate resolveStartDate(GeneralImportConfig config, List<DayRows> days) {
        if ("DATE".equals(normalizeMode(config.dayMode()))) {
            LocalDate firstDate = days.stream()
                    .map(DayRows::date)
                    .filter(date -> date != null)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            if (firstDate != null) return firstDate;
        }
        if (config.startDate() == null) {
            throw new IllegalArgumentException("여행 시작일을 입력해 주세요.");
        }
        return config.startDate();
    }

    private String normalizeDayKey(
            String raw,
            String dayMode,
            PlanImportIssueCollector issues,
            int rowIndex
    ) {
        if (!"DATE".equals(normalizeMode(dayMode))) return raw.trim();
        LocalDate date = entryParser.parseDate(raw);
        if (date == null) {
            issues.addRowIssue(rowIndex, "ERROR", "날짜를 읽을 수 없어 제외했습니다.", raw);
            return null;
        }
        return date.toString();
    }

    private void validateConfig(GeneralImportConfig config) {
        if (config == null) throw new IllegalArgumentException("Import 설정이 필요합니다.");
        if (config.planName() == null || config.planName().isBlank()
                || config.planName().length() > 200) {
            throw new IllegalArgumentException("계획 이름은 1~200자로 입력해 주세요.");
        }
        if (config.startDate() != null
                && (config.startDate().getYear() < 1900 || config.startDate().getYear() > 2100)) {
            throw new IllegalArgumentException("여행 시작일의 연도는 1900년부터 2100년 사이여야 합니다.");
        }
        if (config.columns() == null || !config.columns().containsKey("place")) {
            throw new IllegalArgumentException("장소명 열을 선택해 주세요.");
        }
        if ("TYPE_COLUMN".equals(normalizeMode(config.rowMode()))
                && !config.columns().containsKey("rowType")) {
            throw new IllegalArgumentException("장소와 이동을 구분할 행 종류 열을 선택해 주세요.");
        }
    }

    private String mapped(List<String> row, GeneralImportConfig config, String key) {
        return entryParser.mapped(row, config, key);
    }

    private boolean hasMappedContent(List<String> row, GeneralImportConfig config) {
        if (config.columns() == null || config.columns().isEmpty()) return false;
        return config.columns().values().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .anyMatch(index -> index >= 0 && index < row.size() && !row.get(index).isBlank());
    }

    private boolean hasScheduleContent(List<String> row, GeneralImportConfig config) {
        return List.of(
                        "start", "duration", "end", "offset", "movingDuration",
                        "movingOffset", "transport", "memo", "movingMemo", "rowType"
                ).stream()
                .anyMatch(key -> !mapped(row, config, key).isBlank());
    }

    private String normalizeMode(String value) {
        return entryParser.normalizeMode(value);
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "가져온 파일";
        return fileName.replaceAll("[\\r\\n]", "").trim();
    }
}
