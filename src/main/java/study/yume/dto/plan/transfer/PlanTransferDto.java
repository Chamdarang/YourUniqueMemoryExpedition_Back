package study.yume.dto.plan.transfer;

import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanTransferDto(
        Integer formatVersion,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer planDays,
        String planMemo,
        List<Day> days
) {
    public record Day(
            String dayName,
            Integer dayOrder,
            String memo,
            List<Schedule> schedules
    ) {
    }

    public record Schedule(
            Integer scheduleOrder,
            Long spotUserId,
            String spotName,
            SpotType spotType,
            Double lat,
            Double lng,
            Boolean isChecked,
            LocalTime startTime,
            Boolean fixedStartTime,
            Integer duration,
            LocalTime endTime,
            Integer movingDuration,
            Integer extraDuration,
            Integer extraMovingDuration,
            Transportation transportation,
            String memo,
            String movingMemo
    ) {
    }
}
