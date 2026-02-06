package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.spotgroup.request.SpotGroupCreateRequest;
import study.yume.dto.spotgroup.request.SpotGroupUpdateRequest;
import study.yume.dto.spotgroup.response.SpotGroupDetailResponse;
import study.yume.dto.spotgroup.response.SpotGroupResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.SpotGroupService;

import java.util.List;

@RestController
@RequestMapping("api/groups")
@RequiredArgsConstructor
public class SpotGroupController {
    private final SpotGroupService spotGroupService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SpotGroupResponse>>> getAllGroups(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(spotGroupService.getAllGroups(user.getId())));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<SpotGroupDetailResponse>> getGroupById(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(spotGroupService.getGroupById(user.getId(),groupId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SpotGroupResponse>> createGroup(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody SpotGroupCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(spotGroupService.createGroup(user.getId(),req)));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<ApiResponse<SpotGroupResponse>> updateGroup(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long groupId,
            @RequestBody SpotGroupUpdateRequest req){
        return ResponseEntity.ok(ApiResponse.success(spotGroupService.updateGroup(user.getId(),groupId,req)));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long groupId){
        spotGroupService.deleteGroup(user.getId(),groupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{groupId}/spot/{spotUserId}")
    public ResponseEntity<ApiResponse<Void>> addSpotToGroup(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long groupId,
            @PathVariable Long spotUserId){
        spotGroupService.addSpotToGroup(user.getId(),groupId,spotUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{groupId}/spot/{spotUserId}")
    public ResponseEntity<ApiResponse<Void>> removeSpotFromGroup(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long groupId,
            @PathVariable Long spotUserId) {
        spotGroupService.removeSpotFromGroup(user.getId(),groupId,spotUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
