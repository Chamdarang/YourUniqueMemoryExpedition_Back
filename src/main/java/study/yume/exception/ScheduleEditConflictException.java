package study.yume.exception;

import lombok.Getter;
import study.yume.dto.schedule.response.DayScheduleResponse;

@Getter
public class ScheduleEditConflictException extends RuntimeException {
    private final DayScheduleResponse latestSchedule;

    public ScheduleEditConflictException(String message, DayScheduleResponse latestSchedule) {
        super(message);
        this.latestSchedule = latestSchedule;
    }
}
