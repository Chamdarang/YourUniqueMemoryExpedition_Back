package study.yume.dto.schedule.response;

public record UsedScheduleResponse(
        Long planId,
        Long dayId,
        String planName,
        String dayName,
        Integer scheduleOrder
) {
    public UsedScheduleResponse {
        if (planName == null) planName = "독립 일정";
    }
}