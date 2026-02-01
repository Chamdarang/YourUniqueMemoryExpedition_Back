package study.yume.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import study.yume.dto.ApiResponse;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 응답 헤더 및 상태 코드 설정
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");

        // ApiResponse 규격에 맞춘 에러 메시지 생성
        ApiResponse<Void> apiResponse = ApiResponse.error("인증이 만료되었거나 유효하지 않습니다.");

        // JSON으로 변환하여 출력
        String result = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(result);
    }
}