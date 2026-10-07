package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import study.yume.dto.ApiResponse;
import study.yume.dto.account.request.ChangePasswordRequest;
import study.yume.dto.account.request.DeleteAccountRequest;
import study.yume.dto.account.request.UpdateNicknameRequest;
import study.yume.dto.account.response.AccountProfileResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.AccountService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ApiResponse<AccountProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getProfile(user.getId())));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<AccountProfileResponse>> updateNickname(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UpdateNicknameRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(accountService.updateNickname(user.getId(), request)));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ChangePasswordRequest request
    ) {
        accountService.changePassword(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody DeleteAccountRequest request
    ) {
        accountService.deleteAccount(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
