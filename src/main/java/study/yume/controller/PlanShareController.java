package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import study.yume.dto.ApiResponse;
import study.yume.dto.planshare.response.PlanShareLinkResponse;
import study.yume.dto.planshare.response.PlanShareStatusResponse;
import study.yume.dto.planshare.response.PublicPlanShareResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanShareService;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PlanShareController {
    private final PlanShareService planShareService;

    @GetMapping("/api/plans/{planId}/share")
    public ResponseEntity<ApiResponse<PlanShareStatusResponse>> getShareStatus(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(planShareService.getStatus(user.getId(), planId)));
    }

    @PostMapping("/api/plans/{planId}/share")
    public ResponseEntity<ApiResponse<PlanShareLinkResponse>> createShareLink(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(planShareService.createOrRotate(user.getId(), planId)));
    }

    @DeleteMapping("/api/plans/{planId}/share")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        planShareService.revoke(user.getId(), planId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.<Void>success(null));
    }

    @GetMapping("/api/public/plans/share")
    public ResponseEntity<ApiResponse<PublicPlanShareResponse>> getPublicPlan(
            @RequestHeader("X-Plan-Share-Token") String token
    ) {
        Optional<PublicPlanShareResponse> response = planShareService.getPublicPlan(token);
        return response
                .map(data -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(ApiResponse.<PublicPlanShareResponse>success(data)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .cacheControl(CacheControl.noStore())
                        .body(ApiResponse.<PublicPlanShareResponse>error("공유 링크가 만료되었거나 더 이상 사용할 수 없습니다.")));
    }
}
