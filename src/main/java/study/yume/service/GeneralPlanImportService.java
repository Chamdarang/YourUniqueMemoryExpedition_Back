package study.yume.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.dto.plan.response.PlanImportAnalysisResponse;
import study.yume.dto.plan.response.PlanImportPreviewResponse;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class GeneralPlanImportService {

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_SHEETS = 20;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_PREVIEW_SAMPLES = 5;
    private static final Set<String> KNOWN_HEADERS = Set.of(
            "일자", "날짜", "장소", "장소명", "목적", "시작", "시작시간",
            "소요시간", "체류시간", "종료", "종료시간", "교통", "교통수단", "비고", "메모"
    );

    public PlanImportAnalysisResponse analyze(
            MultipartFile file,
            String charset,
            String delimiter
    ) {
        TableFile table = read(file, charset, delimiter);
        List<PlanImportAnalysisResponse.Sheet> sheets = table.sheets().stream()
                .map(this::analyzeSheet)
                .toList();
        return new PlanImportAnalysisResponse(
                file.getOriginalFilename(),
                table.fileType(),
                table.charset(),
                table.delimiter(),
                sheets
        );
    }

    public PlanImportPreviewResponse preview(MultipartFile file, GeneralImportConfig config) {
        validateConfig(config);
        TableFile table = read(file, config.csvCharset(), config.csvDelimiter());
        Set<String> selectedSheets = config.sheetNames() == null
                ? Set.of()
                : new LinkedHashSet<>(config.sheetNames());
        List<TableSheet> sheets = table.sheets().stream()
                .filter(sheet -> selectedSheets.isEmpty() || selectedSheets.contains(sheet.name()))
                .toList();
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("가져올 시트를 하나 이상 선택해 주세요.");
        }

        List<PlanImportPreviewResponse.Issue> issues = new ArrayList<>();
        Map<String, DayRows> grouped = new LinkedHashMap<>();
        int sourceRows = 0;
        int dataStartIndex = Math.max(
                0,
                valueOr(config.dataStartRow(), valueOr(config.headerRow(), 1) + 1) - 1
        );
        String inheritedDay = "";

        for (int sheetIndex = 0; sheetIndex < sheets.size(); sheetIndex++) {
            TableSheet sheet = sheets.get(sheetIndex);
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
                    issues.add(issue(rowIndex, "ERROR", "일차 또는 날짜를 확인할 수 없어 제외했습니다.", place));
                    continue;
                }
                String dayKey = normalizeDayKey(dayValue, config.dayMode(), issues, rowIndex);
                if (dayKey == null) continue;
                String resolvedDayValue = dayValue;
                DayRows day = grouped.computeIfAbsent(dayKey, ignored ->
                        new DayRows(resolvedDayValue, parseDate(resolvedDayValue), new ArrayList<>(), new ArrayList<>()));
                if (!dayMemo.isBlank() && !day.memos().contains(dayMemo.trim())) {
                    day.memos().add(dayMemo.trim());
                }
                if (place.isBlank()) {
                    if (hasScheduleContent(row, config)) {
                        issues.add(issue(rowIndex, "ERROR", "장소명 또는 목적이 비어 있어 제외했습니다.", ""));
                    }
                    continue;
                }

                RawEntry entry = parseEntry(row, rowIndex, place, config, issues);
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
            List<PlanTransferDto.Schedule> schedules = buildSchedules(rawDay, config, issues);
            fixedStartTimes += (int) schedules.stream()
                    .filter(schedule -> Boolean.TRUE.equals(schedule.fixedStartTime()))
                    .count();
            schedules.stream()
                    .map(PlanTransferDto.Schedule::spotName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(this::normalizeText)
                    .forEach(uniquePlaces::add);
            days.add(new PlanTransferDto.Day(
                    (index + 1) + "일차" + (rawDay.label().isBlank() ? "" : " (" + rawDay.label() + ")"),
                    index + 1,
                    String.join("\n\n", rawDay.memos()),
                    schedules
            ));
        }

        int importedSchedules = days.stream().mapToInt(day -> day.schedules().size()).sum();
        if (importedSchedules == 0) {
            throw new IllegalArgumentException("가져올 수 있는 일정이 없습니다.");
        }
        long skippedRows = issues.stream().filter(issue -> "ERROR".equals(issue.severity())).count();
        PlanTransferDto plan = new PlanTransferDto(
                1,
                config.planName().trim(),
                resolvedStartDate,
                resolvedStartDate.plusDays(days.size() - 1L),
                days.size(),
                "범용 파일 Import: " + safeFileName(file.getOriginalFilename()),
                days
        );
        return new PlanImportPreviewResponse(
                plan,
                new PlanImportPreviewResponse.Summary(
                        sourceRows,
                        days.size(),
                        importedSchedules,
                        (int) skippedRows,
                        fixedStartTimes,
                        uniquePlaces.size()
                ),
                issues
        );
    }

    private PlanImportAnalysisResponse.Sheet analyzeSheet(TableSheet sheet) {
        int headerIndex = suggestHeaderRow(sheet.rows());
        int maxColumns = sheet.rows().stream().mapToInt(List::size).max().orElse(0);
        List<PlanImportAnalysisResponse.Column> columns = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < maxColumns; columnIndex++) {
            String header = value(sheet.rows(), headerIndex, columnIndex);
            String label = header.isBlank() ? columnLabel(columnIndex) : header;
            List<String> samples = new ArrayList<>();
            for (int rowIndex = headerIndex + 1;
                 rowIndex < sheet.rows().size() && samples.size() < MAX_PREVIEW_SAMPLES;
                 rowIndex++) {
                String sample = value(sheet.rows(), rowIndex, columnIndex);
                if (!sample.isBlank()) samples.add(sample);
            }
            columns.add(new PlanImportAnalysisResponse.Column(columnIndex, label, samples));
        }
        return new PlanImportAnalysisResponse.Sheet(
                sheet.name(),
                sheet.rows().size(),
                headerIndex + 1,
                columns
        );
    }

    private RawEntry parseEntry(
            List<String> row,
            int rowIndex,
            String originalPlace,
            GeneralImportConfig config,
            List<PlanImportPreviewResponse.Issue> issues
    ) {
        String place = originalPlace.trim();
        String memo = mapped(row, config, "memo");
        if (Boolean.TRUE.equals(config.firstLineAsPlaceName()) && place.contains("\n")) {
            String[] lines = place.split("\\R", 2);
            place = lines[0].trim();
            memo = combine(lines.length > 1 ? lines[1].trim() : "", memo);
        }

        boolean movement = isMovementRow(row, place, config);
        LocalTime start = parseTime(mapped(row, config, "start"));
        Integer duration = parseDuration(
                mapped(row, config, movement ? "movingDuration" : "duration"),
                movement ? config.movingDurationUnit() : config.durationUnit()
        );
        if (movement && duration == null) {
            duration = parseDuration(mapped(row, config, "duration"), config.durationUnit());
        }
        LocalTime end = parseTime(mapped(row, config, "end"));
        if (movement && duration == null && start != null && end != null) {
            duration = minutesBetween(start, end);
        }
        Integer offset = parseDuration(
                mapped(row, config, movement ? "movingOffset" : "offset"),
                "MINUTES"
        );
        if (movement && offset == null) {
            offset = parseDuration(mapped(row, config, "offset"), "MINUTES");
        }
        String transportationSource = mapped(row, config, "transport");
        if (transportationSource.isBlank() && movement) {
            transportationSource = originalPlace;
        }
        Transportation transportation = parseTransportation(transportationSource, config);
        String movingMemo = mapped(row, config, "movingMemo");

        if (!mapped(row, config, "start").isBlank() && start == null) {
            issues.add(issue(rowIndex, "ERROR", "시작시간을 읽을 수 없어 제외했습니다.", mapped(row, config, "start")));
            return null;
        }
        boolean ambiguous = !movement
                && start == null
                && duration == null
                && end == null
                && memo.isBlank();
        if (ambiguous) {
            issues.add(issue(rowIndex, "WARNING", "장소인지 설명인지 확인해 주세요.", place));
        }
        return new RawEntry(
                rowIndex + 1,
                place,
                movement,
                movement ? destination(place) : place,
                start,
                duration,
                end,
                valueOr(offset, 0),
                transportation,
                memo,
                movingMemo.isBlank() ? originalPlace.trim() : movingMemo
        );
    }

    private List<PlanTransferDto.Schedule> buildSchedules(
            DayRows day,
            GeneralImportConfig config,
            List<PlanImportPreviewResponse.Issue> issues
    ) {
        List<DraftSchedule> drafts = new ArrayList<>();
        List<RawEntry> pendingMovements = new ArrayList<>();

        for (RawEntry row : day.rows()) {
            if (row.movement()) {
                if (!row.destination().isBlank()) {
                    appendArrowMovement(drafts, row);
                    continue;
                }
                if (!drafts.isEmpty() && row.start() != null) {
                    DraftSchedule previous = drafts.get(drafts.size() - 1);
                    if (previous.durationMinutes == null && previous.startTime != null) {
                        previous.durationMinutes = minutesBetween(previous.startTime, row.start());
                        previous.endTime = row.start();
                    }
                }
                pendingMovements.add(row);
                continue;
            }

            DraftSchedule draft = DraftSchedule.from(row);
            DraftSchedule previous = drafts.isEmpty() ? null : drafts.get(drafts.size() - 1);
            if (previous != null
                    && previous.fromMovement
                    && valueOr(previous.durationMinutes, 0) == 0
                    && previous.startTime != null
                    && previous.startTime.equals(draft.startTime)) {
                previous.durationMinutes = draft.durationMinutes;
                previous.extraDuration = draft.extraDuration;
                previous.endTime = draft.endTime;
                previous.memo = combine(row.place(), row.memo());
                previous.spotType = inferSpotType(previous.spotName, row.place());
                continue;
            }
            attachMovements(draft, pendingMovements);
            pendingMovements.clear();
            drafts.add(draft);
        }

        if (!pendingMovements.isEmpty()) {
            String destination = pendingMovements.get(pendingMovements.size() - 1).destination();
            if (destination.isBlank()) {
                RawEntry last = pendingMovements.get(pendingMovements.size() - 1);
                issues.add(new PlanImportPreviewResponse.Issue(
                        last.rowNumber(),
                        "ERROR",
                        "마지막 이동 행에서 목적지를 추출할 수 없어 제외했습니다.",
                        last.place()
                ));
            } else {
                DraftSchedule generated = new DraftSchedule();
                generated.spotName = destination;
                generated.spotType = inferSpotType(destination);
                generated.memo = "";
                generated.explicitStart = false;
                attachMovements(generated, pendingMovements);
                RawEntry lastMove = pendingMovements.get(pendingMovements.size() - 1);
                RawEntry firstMove = pendingMovements.get(0);
                if (firstMove.start() != null) {
                    generated.startTime = firstMove.start().plusMinutes(generated.movingDuration);
                }
                drafts.add(generated);
            }
        }

        LocalTime defaultStart = config.defaultStartTime() == null
                ? LocalTime.of(9, 0)
                : config.defaultStartTime();
        int defaultDuration = positiveOrDefault(config.defaultDurationMinutes(), 60);
        int lastDuration = positiveOrDefault(config.lastDurationMinutes(), defaultDuration);

        for (int index = 0; index < drafts.size(); index++) {
            DraftSchedule current = drafts.get(index);
            if (current.startTime == null) {
                if (index == 0) {
                    current.startTime = defaultStart;
                    current.fixedStartTime = true;
                } else {
                    DraftSchedule previous = drafts.get(index - 1);
                    current.startTime = previous.endTime.plusMinutes(current.movingDuration);
                    current.fixedStartTime = false;
                }
            } else {
                current.fixedStartTime = current.explicitStart;
            }

            if (current.durationMinutes == null) {
                DraftSchedule next = index + 1 < drafts.size() ? drafts.get(index + 1) : null;
                if (next != null && next.startTime != null) {
                    current.durationMinutes = Math.max(
                            0,
                            minutesBetween(current.startTime, next.startTime) - next.movingDuration
                    );
                } else {
                    current.durationMinutes = index == drafts.size() - 1
                            ? lastDuration
                            : defaultDuration;
                }
            }
            current.endTime = current.startTime.plusMinutes(current.durationMinutes);
        }

        List<PlanTransferDto.Schedule> schedules = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            DraftSchedule draft = drafts.get(index);
            schedules.add(new PlanTransferDto.Schedule(
                    index,
                    null,
                    truncate(draft.spotName, 200),
                    draft.spotType,
                    null,
                    null,
                    false,
                    draft.startTime,
                    draft.fixedStartTime,
                    draft.durationMinutes,
                    draft.endTime,
                    draft.movingDuration,
                    draft.extraDuration,
                    draft.extraMovingDuration,
                    draft.transportation,
                    truncate(draft.memo, 500),
                    truncate(draft.movingMemo, 500)
            ));
        }
        return schedules;
    }

    private void appendArrowMovement(List<DraftSchedule> drafts, RawEntry movement) {
        String origin = origin(movement.place());
        DraftSchedule previous = drafts.isEmpty() ? null : drafts.get(drafts.size() - 1);

        if (previous != null && movement.start() != null
                && previous.durationMinutes == null && previous.startTime != null) {
            previous.durationMinutes = minutesBetween(previous.startTime, movement.start());
            previous.endTime = movement.start();
        }

        if (!origin.isBlank() && (previous == null || !samePlace(previous.spotName, origin))) {
            DraftSchedule originDraft = new DraftSchedule();
            originDraft.spotName = origin;
            originDraft.spotType = inferSpotType(origin);
            originDraft.startTime = movement.start();
            originDraft.durationMinutes = 0;
            originDraft.endTime = movement.start();
            originDraft.explicitStart = movement.start() != null;
            originDraft.memo = "";
            drafts.add(originDraft);
        }

        DraftSchedule destinationDraft = new DraftSchedule();
        destinationDraft.spotName = movement.destination();
        destinationDraft.spotType = inferSpotType(movement.destination());
        destinationDraft.durationMinutes = 0;
        destinationDraft.memo = "";
        destinationDraft.explicitStart = false;
        destinationDraft.fromMovement = true;
        attachMovements(destinationDraft, List.of(movement));
        if (movement.end() != null) {
            destinationDraft.startTime = movement.end();
        } else if (movement.start() != null) {
            destinationDraft.startTime = movement.start().plusMinutes(destinationDraft.movingDuration);
        }
        destinationDraft.endTime = destinationDraft.startTime;
        drafts.add(destinationDraft);
    }

    private void attachMovements(DraftSchedule draft, List<RawEntry> movements) {
        if (movements.isEmpty()) return;
        draft.movingDuration = movements.stream()
                .mapToInt(row -> valueOr(row.duration(), 0) + row.offset())
                .sum();
        draft.extraMovingDuration = movements.stream().mapToInt(RawEntry::offset).sum();
        List<Transportation> transports = movements.stream()
                .map(RawEntry::transportation)
                .filter(value -> value != null)
                .distinct()
                .toList();
        draft.transportation = transports.size() == 1 ? transports.get(0) : null;
        draft.movingMemo = movements.stream()
                .map(RawEntry::movingMemo)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", this::combine);
    }

    private boolean isMovementRow(List<String> row, String place, GeneralImportConfig config) {
        return switch (normalizeMode(config.rowMode())) {
            case "ARROW" -> place.contains("→") || place.contains("->");
            case "TYPE_COLUMN" -> {
                String type = normalizeText(mapped(row, config, "rowType"));
                yield config.movementTypeValues() != null
                        && config.movementTypeValues().stream()
                        .map(this::normalizeText)
                        .anyMatch(type::equals);
            }
            default -> false;
        };
    }

    private Transportation parseTransportation(String raw, GeneralImportConfig config) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = normalizeText(raw);
        if (config.transportationMappings() != null) {
            for (Map.Entry<String, Transportation> entry : config.transportationMappings().entrySet()) {
                if (normalizeText(entry.getKey()).equals(normalized)) return entry.getValue();
            }
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("도보") || lower.contains("걷")) return Transportation.WALK;
        if (lower.contains("버스") || lower.contains("셔틀")) return Transportation.BUS;
        if (lower.contains("jr") || lower.contains("전철") || lower.contains("열차")
                || lower.contains("지하철") || lower.contains("신칸센")) return Transportation.TRAIN;
        if (lower.contains("렌터") || lower.contains("자동차") || lower.contains("자가용")
                || lower.contains("차량")) return Transportation.CAR;
        if (lower.contains("택시")) return Transportation.TAXI;
        if (lower.contains("자전거")) return Transportation.BICYCLE;
        if (lower.contains("오토바이") || lower.contains("바이크")) return Transportation.MOTORCYCLE;
        if (lower.contains("비행") || lower.contains("항공")) return Transportation.AIRPLANE;
        if (lower.contains("페리") || lower.contains("선박") || lower.contains("배")) return Transportation.SHIP;
        return null;
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
            List<PlanImportPreviewResponse.Issue> issues,
            int rowIndex
    ) {
        if (!"DATE".equals(normalizeMode(dayMode))) return raw.trim();
        LocalDate date = parseDate(raw);
        if (date == null) {
            issues.add(issue(rowIndex, "ERROR", "날짜를 읽을 수 없어 제외했습니다.", raw));
            return null;
        }
        return date.toString();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d")
        )) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 다음 형식을 시도한다.
            }
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("M/d"),
                DateTimeFormatter.ofPattern("M월 d일")
        )) {
            try {
                return MonthDay.parse(value, formatter).atYear(LocalDate.now().getYear());
            } catch (DateTimeParseException ignored) {
                // 다음 형식을 시도한다.
            }
        }
        return null;
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().replace("오전", "AM").replace("오후", "PM");
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("a h:mm", Locale.ENGLISH)
        )) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 다음 형식을 시도한다.
            }
        }
        try {
            double numeric = Double.parseDouble(value);
            if (numeric >= 0 && numeric < 1) {
                int minutes = (int) Math.round(numeric * 24 * 60);
                return LocalTime.of((minutes / 60) % 24, minutes % 60);
            }
        } catch (NumberFormatException ignored) {
            // 시간 형식이 아니다.
        }
        return null;
    }

    private Integer parseDuration(String raw, String unit) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.contains("시간") || value.contains("분")) {
                int hours = extractNumberBefore(value, "시간");
                int minutes = extractNumberBefore(value, "분");
                return hours * 60 + minutes;
            }
            if (value.contains(":")) {
                String[] parts = value.split(":");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[0].trim()) * 60
                            + Integer.parseInt(parts[1].trim());
                }
            }
            double numeric = new BigDecimal(value.replace(",", "")).doubleValue();
            return switch (normalizeMode(unit)) {
                case "HOURS" -> (int) Math.round(numeric * 60);
                case "EXCEL" -> (int) Math.round(numeric * 24 * 60);
                case "MINUTES" -> (int) Math.round(numeric);
                default -> numeric >= 0 && numeric < 1
                        ? (int) Math.round(numeric * 24 * 60)
                        : (int) Math.round(numeric);
            };
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int extractNumberBefore(String value, String suffix) {
        int suffixIndex = value.indexOf(suffix);
        if (suffixIndex < 0) return 0;
        int start = suffixIndex - 1;
        while (start >= 0 && Character.isDigit(value.charAt(start))) start--;
        String number = value.substring(start + 1, suffixIndex).trim();
        return number.isBlank() ? 0 : Integer.parseInt(number);
    }

    private TableFile read(MultipartFile file, String charsetName, String delimiterValue) {
        validateFile(file);
        String extension = extension(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            if ("csv".equals(extension)) {
                String detectedCharset = resolveCharset(bytes, charsetName);
                char delimiter = resolveDelimiter(bytes, detectedCharset, delimiterValue);
                return new TableFile(
                        "CSV",
                        detectedCharset,
                        Character.toString(delimiter),
                        List.of(readCsv(bytes, detectedCharset, delimiter, file.getOriginalFilename()))
                );
            }
            if (!Set.of("xlsx", "xls").contains(extension)) {
                throw new IllegalArgumentException("XLSX, XLS, CSV 파일만 지원합니다.");
            }
            return new TableFile(
                    extension.toUpperCase(Locale.ROOT),
                    "",
                    "",
                    readWorkbook(bytes)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.error("general_import_read_error filename={} size={}",
                    file.getOriginalFilename(), file.getSize(), exception);
            throw new IllegalArgumentException("파일을 읽지 못했습니다. 형식과 인코딩을 확인해 주세요.", exception);
        }
    }

    private List<TableSheet> readWorkbook(byte[] bytes) throws IOException {
        List<TableSheet> sheets = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        int totalRows = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                throw new IllegalArgumentException("시트는 최대 " + MAX_SHEETS + "개까지 가져올 수 있습니다.");
            }
            for (Sheet sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<String> values = new ArrayList<>();
                    if (row != null) {
                        for (int column = 0; column < Math.max(0, row.getLastCellNum()); column++) {
                            values.add(formatCell(row.getCell(column), formatter));
                        }
                    }
                    rows.add(trimTrailingEmpty(values));
                    totalRows++;
                    if (totalRows > MAX_ROWS) {
                        throw new IllegalArgumentException("데이터 행은 최대 " + MAX_ROWS + "개까지 가져올 수 있습니다.");
                    }
                }
                sheets.add(new TableSheet(sheet.getSheetName(), rows));
            }
        }
        return sheets;
    }

    private String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell).trim();
        }
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> formatter.formatRawCellContents(
                    cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(),
                    cell.getCellStyle().getDataFormatString()
            ).trim();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private TableSheet readCsv(
            byte[] bytes,
            String charsetName,
            char delimiter,
            String fileName
    ) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .get();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), Charset.forName(charsetName));
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new IllegalArgumentException("데이터 행은 최대 " + MAX_ROWS + "개까지 가져올 수 있습니다.");
                }
                List<String> values = new ArrayList<>();
                record.forEach(value -> values.add(value == null ? "" : value.trim()));
                if (rows.isEmpty() && !values.isEmpty() && values.get(0).startsWith("\uFEFF")) {
                    values.set(0, values.get(0).substring(1));
                }
                rows.add(trimTrailingEmpty(values));
            }
        }
        return new TableSheet(safeFileName(fileName), rows);
    }

    private String resolveCharset(byte[] bytes, String requested) {
        if (requested != null && !requested.isBlank() && !"AUTO".equalsIgnoreCase(requested)) {
            return Charset.forName(requested).name();
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8.name();
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8.name();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("MS949").name();
        }
    }

    private char resolveDelimiter(byte[] bytes, String charsetName, String requested) {
        if (requested != null && !requested.isBlank() && !"AUTO".equalsIgnoreCase(requested)) {
            return switch (requested.toUpperCase(Locale.ROOT)) {
                case "TAB", "\\T", "\\t" -> '\t';
                case "SEMICOLON" -> ';';
                default -> requested.charAt(0);
            };
        }
        String text = new String(bytes, Charset.forName(charsetName));
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (char candidate : List.of(',', '\t', ';')) {
            counts.put(candidate, countOutsideQuotes(firstLine, candidate));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    private int countOutsideQuotes(String line, char target) {
        int count = 0;
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') quoted = !quoted;
            else if (!quoted && current == target) count++;
        }
        return count;
    }

    private int suggestHeaderRow(List<List<String>> rows) {
        int bestIndex = 0;
        int bestScore = -1;
        for (int rowIndex = 0; rowIndex < Math.min(rows.size(), 30); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int nonBlank = (int) row.stream().filter(value -> !value.isBlank()).count();
            int known = (int) row.stream()
                    .map(String::trim)
                    .filter(KNOWN_HEADERS::contains)
                    .count();
            int score = nonBlank + known * 5;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = rowIndex;
            }
        }
        return bestIndex;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("가져올 파일이 필요합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일은 10MB 이하여야 합니다.");
        }
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
        Integer index = config.columns() == null ? null : config.columns().get(key);
        return index == null || index < 0 || index >= row.size() ? "" : row.get(index).trim();
    }

    private boolean hasMappedContent(List<String> row, GeneralImportConfig config) {
        if (config.columns() == null || config.columns().isEmpty()) return false;
        return config.columns().values().stream()
                .filter(Objects::nonNull)
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

    private String value(List<List<String>> rows, int row, int column) {
        return row < 0 || row >= rows.size() || column < 0 || column >= rows.get(row).size()
                ? ""
                : rows.get(row).get(column);
    }

    private String destination(String text) {
        String normalized = text.replace("→", "->");
        int arrow = normalized.lastIndexOf("->");
        if (arrow < 0) return "";
        return normalized.substring(arrow + 2).split("[/\\n]", 2)[0].trim();
    }

    private String origin(String text) {
        String normalized = text.replace("→", "->");
        int arrow = normalized.indexOf("->");
        if (arrow < 0) return "";
        return normalized.substring(0, arrow).split("[/\\n]", 2)[0].trim();
    }

    private boolean samePlace(String left, String right) {
        return left != null && right != null && normalizeText(left).equals(normalizeText(right));
    }

    private int minutesBetween(LocalTime from, LocalTime to) {
        int difference = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60;
        return difference < 0 ? difference + 24 * 60 : difference;
    }

    private SpotType inferSpotType(String name) {
        return inferSpotType(name, "");
    }

    private SpotType inferSpotType(String name, String activity) {
        String value = (name + " " + activity).toLowerCase(Locale.ROOT);
        if (value.contains("호텔") || value.contains("숙소")) return SpotType.ACCOMMODATION;
        if (value.contains("역") || value.contains("공항") || value.contains("터미널")) return SpotType.STATION;
        if (value.contains("신사") || value.contains("성당") || value.contains("사찰")
                || value.contains("절") || value.contains("신궁")) return SpotType.RELIGIOUS_SITE;
        if (value.contains("공원") || value.contains("정원")) return SpotType.PARK;
        if (value.contains("박물관") || value.contains("미술관") || value.contains("뮤지엄")
                || value.contains("기념관") || value.contains("전시")) return SpotType.MUSEUM;
        if (value.contains("성터") || value.contains("유적")) return SpotType.HISTORICAL_SITE;
        if (value.contains("식당") || value.contains("카페") || value.contains("식사")
                || value.contains("밥") || value.contains("먹") || value.contains("냉면")
                || value.contains("에키벤")) return SpotType.FOOD;
        if (value.contains("쇼핑") || value.contains("가챠") || value.contains("백화점")
                || value.contains("파르코")) return SpotType.SHOPPING;
        if (value.contains("투어") || value.contains("축제") || value.contains("팀랩")
                || value.contains("스튜디오") || value.contains("구경")) return SpotType.ACTIVITY;
        return SpotType.OTHER;
    }

    private String normalizeMode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·ㆍ]+", "");
    }

    private String normalizeText(Object value) {
        return normalizeText(value == null ? "" : value.toString());
    }

    private String combine(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "\n" + second;
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "가져온 파일";
        return fileName.replaceAll("[\\r\\n]", "").trim();
    }

    private String extension(String fileName) {
        String safe = safeFileName(fileName);
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String columnLabel(int index) {
        int value = index + 1;
        StringBuilder label = new StringBuilder();
        while (value > 0) {
            value--;
            label.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return label.toString();
    }

    private List<String> trimTrailingEmpty(List<String> values) {
        int last = values.size();
        while (last > 0 && values.get(last - 1).isBlank()) last--;
        return new ArrayList<>(values.subList(0, last));
    }

    private PlanImportPreviewResponse.Issue issue(
            int zeroBasedRow,
            String severity,
            String message,
            String value
    ) {
        return new PlanImportPreviewResponse.Issue(
                zeroBasedRow + 1,
                severity,
                message,
                truncate(value, 200)
        );
    }

    private record TableFile(
            String fileType,
            String charset,
            String delimiter,
            List<TableSheet> sheets
    ) {
    }

    private record TableSheet(String name, List<List<String>> rows) {
    }

    private record DayRows(String label, LocalDate date, List<RawEntry> rows, List<String> memos) {
    }

    private record RawEntry(
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

    private static final class DraftSchedule {
        private String spotName;
        private SpotType spotType;
        private LocalTime startTime;
        private boolean explicitStart;
        private boolean fixedStartTime;
        private boolean fromMovement;
        private Integer durationMinutes;
        private LocalTime endTime;
        private int movingDuration;
        private int extraDuration;
        private int extraMovingDuration;
        private Transportation transportation;
        private String memo = "";
        private String movingMemo = "";

        private static DraftSchedule from(RawEntry row) {
            DraftSchedule draft = new DraftSchedule();
            draft.spotName = row.place();
            draft.spotType = inferType(row.place());
            draft.startTime = row.start();
            draft.explicitStart = row.start() != null;
            draft.memo = row.memo();
            draft.transportation = null;

            if (row.start() != null && row.end() != null) {
                int actual = between(row.start(), row.end());
                int base = row.duration() == null ? actual : row.duration();
                draft.extraDuration = Math.max(0, actual - base);
                draft.durationMinutes = actual;
                draft.endTime = row.end();
            } else if (row.duration() != null) {
                draft.extraDuration = row.offset();
                draft.durationMinutes = row.duration() + row.offset();
                if (row.start() != null) {
                    draft.endTime = row.start().plusMinutes(draft.durationMinutes);
                }
            }
            return draft;
        }

        private static SpotType inferType(String name) {
            String value = name.toLowerCase(Locale.ROOT);
            if (value.contains("호텔") || value.contains("숙소")) return SpotType.ACCOMMODATION;
            if (value.contains("역") || value.contains("공항") || value.contains("터미널")) return SpotType.STATION;
            if (value.contains("공원") || value.contains("정원")) return SpotType.PARK;
            if (value.contains("박물관") || value.contains("미술관")) return SpotType.MUSEUM;
            if (value.contains("식당") || value.contains("카페") || value.contains("식사")) return SpotType.FOOD;
            return SpotType.OTHER;
        }

        private static int between(LocalTime from, LocalTime to) {
            int difference = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60;
            return difference < 0 ? difference + 24 * 60 : difference;
        }
    }
}
