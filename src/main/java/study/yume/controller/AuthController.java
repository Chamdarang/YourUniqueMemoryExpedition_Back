package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.auth.request.SignupRequest;
import study.yume.security.JwtUtil;
import study.yume.dto.auth.request.LoginRequest;
import study.yume.dto.auth.response.LoginResponse;
import study.yume.security.CustomUserDetailsService;

import java.util.Date;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest req) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 1000*60*60*24);
        String token = jwtUtil.generateToken(req.username(),expiryDate);
        LoginResponse response = new LoginResponse(token, req.username(),expiryDate);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody SignupRequest req) {
        userDetailsService.register(req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/hello")
    public ResponseEntity<ApiResponse<String>> hello() {
        return ResponseEntity.ok(ApiResponse.success("인증된 사용자입니다!"));
    }
}
