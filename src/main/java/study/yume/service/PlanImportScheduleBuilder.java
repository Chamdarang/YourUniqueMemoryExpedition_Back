package study.yume.service;

import org.springframework.stereotype.Component;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.dto.plan.response.PlanImportPreviewResponse;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;
import study.yume.service.PlanImportModels.DayRows;
import study.yume.service.PlanImportModels.RawEntry;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
class PlanImportScheduleBuilder {
    private final PlanImportEntryParser entryParser;

    PlanImportScheduleBuilder(PlanImportEntryParser entryParser) {
        this.entryParser = entryParser;
    }

    List<PlanTransferDto.Schedule> build(DayRows day, GeneralImportConfig config, PlanImportIssueCollector issues) {
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
                        previous.durationMinutes = entryParser.minutesBetween(previous.startTime, row.start());
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
                previous.spotType = entryParser.inferSpotType(previous.spotName, row.place());
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
                generated.spotType = entryParser.inferSpotType(destination);
                generated.memo = "";
                generated.explicitStart = false;
                attachMovements(generated, pendingMovements);
                RawEntry firstMove = pendingMovements.get(0);
                if (firstMove.start() != null) {
                    generated.startTime = firstMove.start().plusMinutes(generated.movingDuration);
                }
                drafts.add(generated);
            }
        }

        buildTimeline(drafts, config);
        return toSchedules(drafts);
    }

    private void buildTimeline(List<DraftSchedule> drafts, GeneralImportConfig config) {
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
                            entryParser.minutesBetween(current.startTime, next.startTime) - next.movingDuration
                    );
                } else {
                    current.durationMinutes = index == drafts.size() - 1
                            ? lastDuration
                            : defaultDuration;
                }
            }
            current.endTime = current.startTime.plusMinutes(current.durationMinutes);
        }
    }

    private List<PlanTransferDto.Schedule> toSchedules(List<DraftSchedule> drafts) {
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
        String origin = entryParser.origin(movement.place());
        DraftSchedule previous = drafts.isEmpty() ? null : drafts.get(drafts.size() - 1);

        if (previous != null && movement.start() != null
                && previous.durationMinutes == null && previous.startTime != null) {
            previous.durationMinutes = entryParser.minutesBetween(previous.startTime, movement.start());
            previous.endTime = movement.start();
        }

        if (!origin.isBlank() && (previous == null || !entryParser.samePlace(previous.spotName, origin))) {
            DraftSchedule originDraft = new DraftSchedule();
            originDraft.spotName = origin;
            originDraft.spotType = entryParser.inferSpotType(origin);
            originDraft.startTime = movement.start();
            originDraft.durationMinutes = 0;
            originDraft.endTime = movement.start();
            originDraft.explicitStart = movement.start() != null;
            originDraft.memo = "";
            drafts.add(originDraft);
        }

        DraftSchedule destinationDraft = new DraftSchedule();
        destinationDraft.spotName = movement.destination();
        destinationDraft.spotType = entryParser.inferSpotType(movement.destination());
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
                .reduce("", PlanImportScheduleBuilder::combine);
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

    private static String combine(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "\n" + second;
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
