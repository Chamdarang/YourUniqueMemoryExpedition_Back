package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.planday.request.PlanDayCreateRequest;
import study.yume.dto.planday.request.PlanDayIndependentCreateRequest;
import study.yume.dto.planday.request.PlanDaySwapRequest;
import study.yume.dto.planday.request.PlanDayUpdateRequest;
import study.yume.dto.planday.response.PlanDayDetailResponse;
import study.yume.dto.planday.response.PlanDayResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanDayService;

import java.util.List;

@RestController
@RequestMapping("/api/days")
@RequiredArgsConstructor
public class PlanDayController {
    private final PlanDayService planDayService;

    @PostMapping("/independent")
    public ResponseEntity<ApiResponse<PlanDayResponse>> createIndependentDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody PlanDayIndependentCreateRequest req){
        return ResponseEntity.ok(ApiResponse.success(planDayService.createIndependentDay(user.getId(),req)));
    }

    @PostMapping("/plan/{planId}")
    public ResponseEntity<ApiResponse<PlanDayResponse>> createPlanDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @RequestBody PlanDayCreateRequest req ){
        return ResponseEntity.ok(ApiResponse.success(planDayService.createPlanDay(user.getId(),planId,req)));
    }

    @GetMapping("/independent")
    public ResponseEntity<ApiResponse<List<PlanDayResponse>>> getAllIndependentDay(
            @AuthenticationPrincipal CustomUserDetails user ){
        return ResponseEntity.ok(ApiResponse.success(planDayService.getAllIndependentDay(user.getId())));
    }

    @GetMapping("/{dayId}")
    public ResponseEntity<ApiResponse<PlanDayDetailResponse>> getPlanDayDetail(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId){
        return ResponseEntity.ok(ApiResponse.success(planDayService.getPlanDayDetail(user.getId(),dayId)));
    }

    @GetMapping("/plan/{planId}")
    public ResponseEntity<ApiResponse<List<PlanDayResponse>>> getPlanDaysByPlanId(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId){
        return ResponseEntity.ok(ApiResponse.success(planDayService.getPlanDaysByPlanId(user.getId(),planId)));
    }

    @PatchMapping("/{dayId}")
    public ResponseEntity<ApiResponse<PlanDayResponse>> updatePlanDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId,
            @RequestBody PlanDayUpdateRequest req){
        return ResponseEntity.ok(ApiResponse.success(planDayService.updatePlanDay(user.getId(),dayId,req)));
    }
    @PostMapping("/{dayId}/detach")
    public ResponseEntity<ApiResponse<PlanDayResponse>> detachPlanDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId){
        return ResponseEntity.ok(ApiResponse.success(planDayService.detachPlanDay(user.getId(),dayId)));
    }

    @PostMapping("/swap")
    public ResponseEntity<ApiResponse<Void>> swapPlanDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody PlanDaySwapRequest req){
        planDayService.swapPlan(user.getId(),req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("{dayId}")
    public ResponseEntity<ApiResponse<Void>> deletePlanDay(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable Long dayId){
        planDayService.deletePlanDay(user.getId(),dayId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
    