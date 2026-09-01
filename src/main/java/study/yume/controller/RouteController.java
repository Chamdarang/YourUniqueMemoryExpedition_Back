package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import study.yume.dto.ApiResponse;
import study.yume.dto.route.request.RouteEstimateRequest;
import study.yume.dto.route.request.DayRouteApplyRequest;
import study.yume.dto.route.response.DayRouteAuditResponse;
import study.yume.dto.route.response.PlanRouteAuditResponse;
import study.yume.dto.route.response.RouteEstimateResponse;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.DayRouteAuditService;
import study.yume.service.RouteEstimateService;
import study.yume.service.DayScheduleService;
import study.yume.service.PlanRouteAuditService;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteEstimateService routeEstimateService;
    private final DayRouteAuditService dayRouteAuditService;
    private final DayScheduleService dayScheduleService;
    private final PlanRouteAuditService planRouteAuditService;

    @PostMapping("/estimate")
    public ResponseEntity<ApiResponse<RouteEstimateResponse>> estimate(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody RouteEstimateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(routeEstimateService.estimate(user.getId(), request)));
    }

    @PostMapping("/day/{dayId}/audit")
    public ResponseEntity<ApiResponse<DayRouteAuditResponse>> auditDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dayRouteAuditService.audit(user.getId(), dayId)));
    }

    @PostMapping("/plan/{planId}/audit")
    public ResponseEntity<ApiResponse<PlanRouteAuditResponse>> auditPlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                planRouteAuditService.audit(user.getId(), planId)
        ));
    }

    @PatchMapping("/day/{dayId}/apply")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> applyDayRouteEstimates(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId,
            @RequestBody DayRouteApplyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dayScheduleService.applyRouteEstimates(user.getId(), dayId, request)
        ));
    }
}
