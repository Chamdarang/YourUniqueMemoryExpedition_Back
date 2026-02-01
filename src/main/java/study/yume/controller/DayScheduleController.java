package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.schedule.request.ScheduleSyncRequest;
import study.yume.dto.schedule.request.ScheduleUpdateMemoRequest;
import study.yume.dto.schedule.response.DayScheduleResponse;
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
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.getSchedulesByDayId(user.getId(),dayId)));
    }

    @PutMapping("/day/{dayId}/sync")
    public ResponseEntity<ApiResponse<List<DayScheduleResponse>>> syncSchedules(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long dayId,
            @RequestBody ScheduleSyncRequest req) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.syncSchedules(user.getId(), dayId, req)));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId){
        dayScheduleService.deleteSchedule(user.getId(), scheduleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    //일정 순서나 시간과 무관한 수정
    @PatchMapping("/{scheduleId}/memo")
    public ResponseEntity<ApiResponse<DayScheduleResponse>> updateMemo(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleUpdateMemoRequest req) {
        return ResponseEntity.ok(ApiResponse.success(dayScheduleService.updateMemo(user.getId(), scheduleId, req)));
    }

}
