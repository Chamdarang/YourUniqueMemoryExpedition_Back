package study.yume.exception;

import lombok.Getter;

@Getter
public class PlanEntityEditConflictException extends RuntimeException {
    private final Object latestValue;

    public PlanEntityEditConflictException(String message, Object latestValue) {
        super(message);
        this.latestValue = latestValue;
    }
}
