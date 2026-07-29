package study.yume.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.auth.request.SignupRequest;
import study.yume.security.JwtUtil;
import study.yume.dto.auth.request.LoginRequest;
import study.yume.dto.auth.response.LoginResponse;
import study.yume.security.AuthRateLimitService;
import study.yume.security.ClientAddressResolver;
import study.yume.security.CustomUserDetailsService;

import java.util.Date;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuthRateLimitService authRateLimitService;
    private final ClientAddressResolver clientAddressResolver;

    @Value("${auth.signup-enabled:true}")
    private boolean signupEnabled;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request
    ) {
        String clientAddress = clientAddressResolver.resolve(request);
        authRateLimitService.checkLoginAllowed(clientAddress, req.username());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
            authRateLimitService.recordLoginSuccess(clientAddress, req.username());
        } catch (AuthenticationException exception) {
            authRateLimitService.recordLoginFailure(clientAddress, req.username());
            throw exception;
        }
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 1000*60*60*24);
        String token = jwtUtil.generateToken(req.username(),expiryDate);
        LoginResponse response = new LoginResponse(token, req.username(),expiryDate);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> register(
            @RequestBody SignupRequest req,
            HttpServletRequest request
    ) {
        if (!signupEnabled) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("현재 회원가입이 비활성화되어 있습니다."));
        }
        authRateLimitService.consumeSignupAttempt(clientAddressResolver.resolve(request));
        userDetailsService.register(req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/hello")
    public ResponseEntity<ApiResponse<String>> hello() {
        return ResponseEntity.ok(ApiResponse.success("인증된 사용자입니다!"));
    }
}
