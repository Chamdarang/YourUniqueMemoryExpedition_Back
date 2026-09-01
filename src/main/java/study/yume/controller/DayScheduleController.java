package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.schedule.request.ScheduleCreateRequest;
import study.yume.dto.schedule.request.ScheduleReorderRequest;
import study.yume.dto.schedule.request.ScheduleUpdateRequest;
import study.yume.dto.schedule.request.ScheduleSpotLinkRequest;
import study.yume.dto.schedule.request.ScheduleTransferRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
import study.yume.dto.schedule.response.ScheduleTransferResponse;
import study.yume.dto.schedule.response.UnlinkedSpotGroupResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.DayScheduleService;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class DayScheduleController {
    private final DayScheduleService dayScheduleService;

    @GetMapping("/day/{dayId}")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> getDaySchedules(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.getSchedulesByDayId(user.getId(), dayId)));
    }

    @PostMapping("day/{dayId}")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> createSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId,
            @RequestBody ScheduleCreateRequest req) {

        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.createSchedule(user.getId(), dayId, req)));
    }

    @PatchMapping("{scheduleId}")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> updateSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleUpdateRequest req) {

        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.updateSchedule(user.getId(), scheduleId,req)));
    }

    @PostMapping("/{scheduleId}/transfer")
    public ResponseEntity<ApiResponse<ScheduleTransferResponse>> transferSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleTransferRequest req) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.transferSchedule(user.getId(), scheduleId, req)));
    }

    @PatchMapping("day/{dayId}/{scheduleId}")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> reorderSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleReorderRequest req){
        return  ResponseEntity.ok(ApiResponse.success(dayScheduleService.reorderSchedule(user.getId(),dayId,scheduleId,req)));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> deleteSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId){

        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.deleteSchedule(user.getId(), scheduleId)));
    }


    @PatchMapping("/{scheduleId}/visit")
    public ResponseEntity<ApiResponse<Void>> updateVisit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId) {
        dayScheduleService.updateVisit(user.getId(),scheduleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{scheduleId}/skip")
    public ResponseEntity<ApiResponse<Void>> toggleScheduleSkip(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId) {
        dayScheduleService.updateSkip(user.getId(), scheduleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/plan/{planId}/unlinked-spots")
    public ResponseEntity<ApiResponse<List<UnlinkedSpotGroupResponse>>> getUnlinkedSpots(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.getUnlinkedSpotGroups(user.getId(), planId)));
    }

    @PatchMapping("/plan/{planId}/link-spot")
    public ResponseEntity<ApiResponse<Integer>> linkSpot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId,
            @RequestBody ScheduleSpotLinkRequest req) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.linkSchedulesToSpot(user.getId(), planId, req)));
    }

    //    @PutMapping("/day/{dayId}/sync")
    //    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> syncSchedules(
    //            @AuthenticationPrincipal CustomUserDetails user,
    //            @PathVariable Long dayId,
    //            @RequestBody ScheduleSyncRequest req) {
    //        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.syncSchedules(user.getId(), dayId, req)));
    //    }

}
