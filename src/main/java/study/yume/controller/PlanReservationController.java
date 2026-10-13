package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.planreservation.request.PlanReservationRequest;
import study.yume.dto.planreservation.response.PlanReservationResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanReservationService;

import java.util.List;

@RestController
@RequestMapping("/api/plans/{planId}/reservations")
@RequiredArgsConstructor
public class PlanReservationController {
    private final PlanReservationService reservationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanReservationResponse>>> getReservations(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.getReservations(user.getId(), planId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlanReservationResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @RequestBody PlanReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.create(user.getId(), planId, request)));
    }

    @PutMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<PlanReservationResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @PathVariable Long reservationId,
            @RequestBody PlanReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reservationService.update(user.getId(), planId, reservationId, request)));
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @PathVariable Long reservationId) {
        reservationService.delete(user.getId(), planId, reservationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
