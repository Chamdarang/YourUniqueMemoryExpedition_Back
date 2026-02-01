package study.yume.dto.auth.request;


public record LoginRequest (
    String username,
    String password
){}
