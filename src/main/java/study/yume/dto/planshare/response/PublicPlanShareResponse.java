package study.yume.dto.planshare.response;

import study.yume.model.enums.ScheduleMode;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PublicPlanShareResponse(
        Long planId,
        String accessMode,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        String planMemo,
        List<Day> days
) {
    public record Day(
            String dayName,
            Integer dayOrder,
            String memo,
            ScheduleMode scheduleMode
    ) {
    }

    public record Schedule(
            Integer scheduleOrder,
            String spotName,
            SpotType spotType,
            Double lat,
            Double lng,
            LocalTime startTime,
            LocalTime endTime,
            Integer duration,
            Integer movingDuration,
            Transportation transportation,
            String memo,
            String movingMemo
    ) {
    }
}
