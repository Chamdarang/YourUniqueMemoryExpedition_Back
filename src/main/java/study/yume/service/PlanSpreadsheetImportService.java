package study.yume.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlanSpreadsheetImportService {

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    public PlanTransferDto preview(MultipartFile file, String planName, LocalDate startDate) {
        validateInput(file, planName, startDate);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = findScheduleSheet(workbook);
            int headerRowIndex = findHeaderRow(sheet);
            List<ImportedDay> importedDays = parseDays(sheet, headerRowIndex);
            if (importedDays.isEmpty()) {
                throw new IllegalArgumentException("엑셀에서 가져올 일정을 찾지 못했습니다.");
            }

            List<PlanTransferDto.Day> days = new ArrayList<>();
            for (int index = 0; index < importedDays.size(); index++) {
                ImportedDay source = importedDays.get(index);
                List<PlanTransferDto.Schedule> schedules = buildSchedules(source.rows());
                days.add(new PlanTransferDto.Day(
                        (index + 1) + "일차 (" + source.label() + ")",
                        index + 1,
                        truncate(source.summary(), 500),
                        schedules
                ));
            }

            return new PlanTransferDto(
                    1,
                    planName.trim(),
                    startDate,
                    startDate.plusDays(days.size() - 1L),
                    days.size(),
                    "Excel 파일에서 가져온 계획: " + truncate(file.getOriginalFilename(), 450),
                    days
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("엑셀 파일을 읽지 못했습니다. 파일 형식을 확인해 주세요.", exception);
        }
    }

    private void validateInput(MultipartFile file, String planName, LocalDate startDate) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("불러올 엑셀 파일이 필요합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("엑셀 파일은 10MB 이하여야 합니다.");
        }
        if (planName == null || planName.isBlank() || planName.length() > 200) {
            throw new IllegalArgumentException("계획 이름은 1~200자로 입력해 주세요.");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("여행 시작일을 입력해 주세요.");
        }
    }

    private Sheet findScheduleSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            try {
                findHeaderRow(sheet);
                return sheet;
            } catch (IllegalArgumentException ignored) {
                // 다음 시트에서 일정 표를 찾는다.
            }
        }
        throw new IllegalArgumentException(
                "일자·목적·시작시간·소요시간 열이 있는 시트를 찾지 못했습니다."
        );
    }

    private int findHeaderRow(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            if ("일자".equals(text(row.getCell(0)))
                    && "목적".equals(text(row.getCell(2)))
                    && "시작시간".equals(text(row.getCell(3)))
                    && "소요시간".equals(text(row.getCell(4)))) {
                return rowIndex;
            }
        }
        throw new IllegalArgumentException("일정 표의 헤더를 찾지 못했습니다.");
    }

    private List<ImportedDay> parseDays(Sheet sheet, int headerRowIndex) {
        List<ImportedDay> days = new ArrayList<>();
        ImportedDay currentDay = null;

        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            String dayLabel = text(row.getCell(0));
            if (!dayLabel.isBlank()) {
                currentDay = new ImportedDay(dayLabel, text(row.getCell(1)), new ArrayList<>());
                days.add(currentDay);
            }
            if (currentDay == null) continue;

            String purpose = text(row.getCell(2));
            LocalTime startTime = time(row.getCell(3));
            LocalTime durationTime = time(row.getCell(4));
            LocalTime endTime = time(row.getCell(5));
            if (purpose.isBlank() || startTime == null || durationTime == null) {
                continue;
            }

            int durationMinutes = durationTime.getHour() * 60 + durationTime.getMinute();
            if (endTime == null) {
                endTime = startTime.plusMinutes(durationMinutes);
            }
            int injuryMinutes = Math.max(
                    0,
                    minutesBetween(startTime, endTime) - durationMinutes
            );
            currentDay.rows().add(new ImportedRow(
                    purpose,
                    startTime,
                    durationMinutes,
                    injuryMinutes,
                    endTime,
                    text(row.getCell(6))
            ));
        }
        return days;
    }

    private List<PlanTransferDto.Schedule> buildSchedules(List<ImportedRow> rows) {
        List<MutableSchedule> schedules = new ArrayList<>();
        for (ImportedRow row : rows) {
            Movement movement = parseMovement(row.purpose());
            if (movement != null) {
                if (schedules.isEmpty() && !movement.origin().isBlank()) {
                    schedules.add(MutableSchedule.origin(movement.origin(), row.startTime()));
                }
                schedules.add(MutableSchedule.movement(
                        movement.destination(),
                        row.endTime(),
                        movement.transportation(),
                        row.durationMinutes() + row.injuryMinutes(),
                        row.injuryMinutes(),
                        combine(row.purpose(), row.note())
                ));
                continue;
            }

            MutableSchedule previous = schedules.isEmpty() ? null : schedules.get(schedules.size() - 1);
            if (previous != null
                    && previous.fromMovement
                    && previous.duration == 0
                    && previous.startTime.equals(row.startTime())) {
                previous.duration = row.durationMinutes() + row.injuryMinutes();
                previous.extraDuration = row.injuryMinutes();
                previous.endTime = row.endTime();
                previous.memo = combine(row.purpose(), row.note());
                previous.spotType = inferSpotType(previous.spotName, row.purpose());
            } else {
                MutableSchedule activity = MutableSchedule.activity(row);
                activity.spotType = inferSpotType(activity.spotName, row.purpose());
                schedules.add(activity);
            }
        }

        List<PlanTransferDto.Schedule> result = new ArrayList<>();
        for (int index = 0; index < schedules.size(); index++) {
            MutableSchedule current = schedules.get(index);
            int movingDuration = 0;
            boolean fixedStartTime = false;
            if (index > 0) {
                MutableSchedule previous = schedules.get(index - 1);
                movingDuration = current.movingDuration;
                LocalTime calculatedStart = previous.endTime.plusMinutes(movingDuration);
                fixedStartTime = !calculatedStart.equals(current.startTime);
            }
            result.add(new PlanTransferDto.Schedule(
                    index,
                    null,
                    truncate(current.spotName, 200),
                    current.spotType,
                    null,
                    null,
                    false,
                    current.startTime,
                    fixedStartTime,
                    current.duration,
                    current.endTime,
                    movingDuration,
                    current.extraDuration,
                    current.extraMovingDuration,
                    current.transportation,
                    truncate(current.memo, 500),
                    truncate(current.movingMemo, 500)
            ));
        }
        return result;
    }

    private Movement parseMovement(String purpose) {
        String normalized = purpose.replace("→", "->");
        String firstLine = normalized.lines().findFirst().orElse(normalized).trim();
        String routePart = firstLine.split("/", 2)[0].trim();
        int arrow = routePart.lastIndexOf("->");
        if (arrow < 0) {
            return null;
        }

        String before = routePart.substring(0, arrow);
        int previousArrow = before.lastIndexOf("->");
        String origin = before.substring(previousArrow >= 0 ? previousArrow + 2 : 0).trim();
        String destination = routePart.substring(arrow + 2).trim();
        if (destination.isBlank()) {
            return null;
        }
        return new Movement(
                origin,
                destination,
                inferTransportation(purpose, origin, destination)
        );
    }

    private Transportation inferTransportation(String text, String origin, String destination) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("비행") || (origin.contains("공항") && destination.contains("공항"))) {
            return Transportation.AIRPLANE;
        }
        if (lower.contains("배") || lower.contains("페리") || lower.contains("선박")) {
            return Transportation.SHIP;
        }
        if (lower.contains("택시")) {
            return Transportation.TAXI;
        }
        if (lower.contains("자전거") || lower.contains("사이클")) {
            return Transportation.BICYCLE;
        }
        if (lower.contains("버스") || lower.contains("셔틀")) {
            return Transportation.BUS;
        }
        if (lower.contains("신칸센")
                || lower.contains("jr")
                || lower.contains("전철")
                || lower.contains("열차")
                || lower.contains("지하철")
                || lower.contains("도영")
                || lower.contains("센세키")
                || lower.contains("익스프레스")
                || lower.contains("뉴셔틀")) {
            return Transportation.TRAIN;
        }
        return Transportation.WALK;
    }

    private SpotType inferSpotType(String spotName, String activity) {
        String text = (spotName + " " + activity).toLowerCase(Locale.ROOT);
        if (text.contains("숙소") || text.contains("호텔")) return SpotType.ACCOMMODATION;
        if (text.contains("역") || text.contains("공항") || text.contains("터미널")) return SpotType.STATION;
        if (text.contains("신사") || text.contains("성당") || text.contains("사찰")
                || text.contains("절") || text.contains("신궁")) return SpotType.RELIGIOUS_SITE;
        if (text.contains("박물관") || text.contains("미술관") || text.contains("뮤지엄")
                || text.contains("기념관") || text.contains("전시")) return SpotType.MUSEUM;
        if (text.contains("공원") || text.contains("정원")) return SpotType.PARK;
        if (text.contains("성터") || text.contains("유적")) return SpotType.HISTORICAL_SITE;
        if (text.contains("밥") || text.contains("먹") || text.contains("식사")
                || text.contains("냉면") || text.contains("에키벤")) return SpotType.FOOD;
        if (text.contains("쇼핑") || text.contains("가챠") || text.contains("백화점")
                || text.contains("파르코")) return SpotType.SHOPPING;
        if (text.contains("투어") || text.contains("축제") || text.contains("팀랩")
                || text.contains("스튜디오")) return SpotType.ACTIVITY;
        return SpotType.OTHER;
    }

    private LocalTime time(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC
                    || cell.getCellType() == CellType.FORMULA) {
                double numeric = cell.getNumericCellValue();
                int totalMinutes = Math.floorMod((int) Math.round(numeric * 24 * 60), 24 * 60);
                return LocalTime.of(totalMinutes / 60, totalMinutes % 60);
            }
            String value = text(cell);
            for (DateTimeFormatter formatter : List.of(
                    DateTimeFormatter.ofPattern("H:mm"),
                    DateTimeFormatter.ofPattern("H:mm:ss")
            )) {
                try {
                    return LocalTime.parse(value, formatter);
                } catch (DateTimeParseException ignored) {
                    // 다음 형식을 시도한다.
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String text(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> Double.toString(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> Double.toString(cell.getNumericCellValue());
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
            default -> "";
        };
    }

    private int minutesBetween(LocalTime from, LocalTime to) {
        int result = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60;
        return result < 0 ? result + 24 * 60 : result;
    }

    private String combine(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "\n" + second;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    private record ImportedDay(String label, String summary, List<ImportedRow> rows) {
    }

    private record ImportedRow(
            String purpose,
            LocalTime startTime,
            int durationMinutes,
            int injuryMinutes,
            LocalTime endTime,
            String note
    ) {
    }

    private record Movement(
            String origin,
            String destination,
            Transportation transportation
    ) {
    }

    private static class MutableSchedule {
        private String spotName;
        private SpotType spotType;
        private LocalTime startTime;
        private int duration;
        private int extraDuration;
        private LocalTime endTime;
        private Transportation transportation;
        private String memo;
        private String movingMemo;
        private int extraMovingDuration;
        private int movingDuration;
        private boolean fromMovement;

        private static MutableSchedule origin(String name, LocalTime time) {
            MutableSchedule schedule = new MutableSchedule();
            schedule.spotName = name;
            schedule.spotType = SpotType.OTHER;
            schedule.startTime = time;
            schedule.duration = 0;
            schedule.endTime = time;
            schedule.transportation = Transportation.WALK;
            schedule.memo = "";
            schedule.movingMemo = "";
            return schedule;
        }

        private static MutableSchedule movement(
                String destination,
                LocalTime arrivalTime,
                Transportation transportation,
                int movingDuration,
                int extraMovingDuration,
                String movingMemo
        ) {
            MutableSchedule schedule = new MutableSchedule();
            schedule.spotName = destination;
            schedule.spotType = destination.contains("역") || destination.contains("공항")
                    ? SpotType.STATION
                    : SpotType.OTHER;
            schedule.startTime = arrivalTime;
            schedule.duration = 0;
            schedule.endTime = arrivalTime;
            schedule.transportation = transportation;
            schedule.memo = "";
            schedule.movingMemo = movingMemo;
            schedule.movingDuration = movingDuration;
            schedule.extraMovingDuration = extraMovingDuration;
            schedule.fromMovement = true;
            return schedule;
        }

        private static MutableSchedule activity(ImportedRow row) {
            MutableSchedule schedule = new MutableSchedule();
            schedule.spotName = row.purpose().lines().findFirst().orElse(row.purpose()).trim();
            schedule.spotType = SpotType.OTHER;
            schedule.startTime = row.startTime();
            schedule.duration = row.durationMinutes() + row.injuryMinutes();
            schedule.extraDuration = row.injuryMinutes();
            schedule.endTime = row.endTime();
            schedule.transportation = Transportation.WALK;
            schedule.memo = row.note();
            schedule.movingMemo = "";
            return schedule;
        }
    }
}
