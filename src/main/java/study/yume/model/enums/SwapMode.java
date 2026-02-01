package study.yume.model.enums;

public enum SwapMode {
    REPLACE, // 기존 일정은 삭제
    INDEPENDENT, // 기존 일정은 독립시킴
    SWAP, // 기존 일정과 자리바꿈
    SHIFT // 기존 일정 앞에 들어가 기존 일정과 이후 일정은 하루씩 밀려남
}
