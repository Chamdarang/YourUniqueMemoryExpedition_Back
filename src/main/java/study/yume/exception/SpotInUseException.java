package study.yume.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class SpotInUseException  extends RuntimeException{
    private final List<UsedScheduleProjection> usedSchedules;

    public SpotInUseException(final List<UsedScheduleProjection> usedSchedules) {
        super("해당 장소를 사용하는 일정이 있어 삭제할 수 없습니다.");
        this.usedSchedules = usedSchedules;
    }
}
