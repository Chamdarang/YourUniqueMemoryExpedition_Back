package study.yume.exception;

public interface UsedScheduleProjection {
    Long getScheduleId();
    Long getPlanId();
    Long getDayId();
    String getPlanName();
    String getDayName();
    Integer getScheduleOrder();
}