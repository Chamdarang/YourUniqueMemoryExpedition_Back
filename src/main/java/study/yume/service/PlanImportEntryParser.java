package study.yume.service;

import org.springframework.stereotype.Component;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;
import study.yume.service.PlanImportModels.RawEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class PlanImportEntryParser {

    RawEntry parseEntry(
            List<String> row,
            int rowIndex,
            String originalPlace,
            GeneralImportConfig config,
            PlanImportIssueCollector issues
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
            issues.addRowIssue(rowIndex, "ERROR", "시작시간을 읽을 수 없어 제외했습니다.", mapped(row, config, "start"));
            return null;
        }
        boolean ambiguous = !movement
                && start == null
                && duration == null
                && end == null
                && memo.isBlank();
        if (ambiguous) {
            issues.addRowIssue(rowIndex, "WARNING", "장소인지 설명인지 확인해 주세요.", place);
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

    LocalDate parseDate(String raw) {
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

    LocalTime parseTime(String raw) {
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

    Integer parseDuration(String raw, String unit) {
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

    boolean isMovementRow(List<String> row, String place, GeneralImportConfig config) {
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

    Transportation parseTransportation(String raw, GeneralImportConfig config) {
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

    SpotType inferSpotType(String name) {
        return inferSpotType(name, "");
    }

    SpotType inferSpotType(String name, String activity) {
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

    String destination(String text) {
        String normalized = text.replace("→", "->");
        int arrow = normalized.lastIndexOf("->");
        if (arrow < 0) return "";
        return normalized.substring(arrow + 2).split("[/\\n]", 2)[0].trim();
    }

    String origin(String text) {
        String normalized = text.replace("→", "->");
        int arrow = normalized.indexOf("->");
        if (arrow < 0) return "";
        return normalized.substring(0, arrow).split("[/\\n]", 2)[0].trim();
    }

    boolean samePlace(String left, String right) {
        return left != null && right != null && normalizeText(left).equals(normalizeText(right));
    }

    int minutesBetween(LocalTime from, LocalTime to) {
        int difference = to.toSecondOfDay() / 60 - from.toSecondOfDay() / 60;
        return difference < 0 ? difference + 24 * 60 : difference;
    }

    String normalizeMode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·ㆍ]+", "");
    }

    String mapped(List<String> row, GeneralImportConfig config, String key) {
        Integer index = config.columns() == null ? null : config.columns().get(key);
        return index == null || index < 0 || index >= row.size() ? "" : row.get(index).trim();
    }

    private int extractNumberBefore(String value, String suffix) {
        int suffixIndex = value.indexOf(suffix);
        if (suffixIndex < 0) return 0;
        int start = suffixIndex - 1;
        while (start >= 0 && Character.isDigit(value.charAt(start))) start--;
        String number = value.substring(start + 1, suffixIndex).trim();
        return number.isBlank() ? 0 : Integer.parseInt(number);
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String combine(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "\n" + second;
    }
}
