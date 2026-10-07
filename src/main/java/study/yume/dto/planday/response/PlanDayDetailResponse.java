package study.yume.dto.planday.response;

import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.model.PlanDay;
import study.yume.model.ScheduleVisitStatus;
import study.yume.model.enums.ScheduleMode;

import java.util.List;
import java.util.Map;

public record PlanDayDetailResponse(
        Long id,
        Long planId,
        String dayName,
        Integer dayOrder,
        String memo,
        ScheduleMode scheduleMode,
        Long version,
        List<DayScheduleResponse> schedules
) {
    public static PlanDayDetailResponse toDto(PlanDay planDay) {
        return toDto(planDay, true);
    }

    public static PlanDayDetailResponse toDto(PlanDay planDay, boolean includeOwnerPrivateData) {
        return toDto(
                planDay,
                planDay.getSchedules().stream()
                        .map(schedule -> DayScheduleResponse.toDto(schedule, includeOwnerPrivateData))
                        .toList()
        );
    }

    public static PlanDayDetailResponse toDto(
            PlanDay planDay,
            boolean includeOwnerPrivateData,
            Map<Long, ScheduleVisitStatus> statusByScheduleId
    ) {
        return new PlanDayDetailResponse(
                planDay.getId(),
                planDay.getPlan() == null ? null : planDay.getPlan().getId(),
                planDay.getDayName(),
                planDay.getDayOrder(),
                planDay.getMemo(),
                planDay.getScheduleMode(),
                planDay.getVersion(),
                planDay.getSchedules().stream()
                        .map(schedule -> {
                            ScheduleVisitStatus status = statusByScheduleId.get(schedule.getId());
                            return DayScheduleResponse.toDto(
                                    schedule,
                                    includeOwnerPrivateData,
                                    status != null && status.isChecked(),
                                    status != null && status.isSkipped()
                            );
                        })
                        .toList()
        );
    }
    public static PlanDayDetailResponse toDto(PlanDay planDay, List<DayScheduleResponse> schedules) {
        return new PlanDayDetailResponse(
                planDay.getId(),
                planDay.getPlan() == null ? null : planDay.getPlan().getId(),
                planDay.getDayName(),
                planDay.getDayOrder(),
                planDay.getMemo(),
                planDay.getScheduleMode(),
                planDay.getVersion(),
                schedules
        );
    }
}
