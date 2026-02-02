package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.plan.request.PlanCreateRequest;
import study.yume.dto.plan.request.PlanUpdateRequest;
import study.yume.dto.plan.response.PlanDetailResponse;
import study.yume.dto.plan.response.PlanResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {
    private final PlanService planService;

    @GetMapping("/FORTEST/GETALLPLANS")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getPlans(
            @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(sort = "createdAt",direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(planService.getAllPlans(user.getId(),pageable)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getFilteredPlans(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Integer> months,
            @PageableDefault(sort = "createdAt",direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(planService.getFilteredPlans(user.getId(), from, to, months, pageable)));
    }
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<PlanResponse>> getUpcomingPlan(
            @AuthenticationPrincipal CustomUserDetails user ){
        return ResponseEntity.ok(ApiResponse.success(planService.getUpcomingPlan(user.getId())));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> getPlanById(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlanById(user.getId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody PlanCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(planService.createPlan(user.getId(), req)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @RequestBody PlanUpdateRequest req){
        return ResponseEntity.ok(ApiResponse.success(planService.updatePlan(user.getId(), id,req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        planService.deletePlanById(user.getId(),id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}