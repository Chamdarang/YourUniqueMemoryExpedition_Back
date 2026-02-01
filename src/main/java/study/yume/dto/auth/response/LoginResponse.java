package study.yume.dto.auth.response;


import java.util.Date;

public record LoginResponse (
        String token,
        String username,
        Date expiryDate
){ }
