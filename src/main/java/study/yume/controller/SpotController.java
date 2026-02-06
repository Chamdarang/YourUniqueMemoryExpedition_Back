package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.spot.request.SpotCreateRequest;
import study.yume.dto.spot.request.SpotGetFilteredRequest;
import study.yume.dto.spot.request.SpotUpdateRequest;
import study.yume.dto.spot.response.SpotDetailResponse;
import study.yume.dto.spot.response.SpotResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.SpotService;


@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {
    private final SpotService spotService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SpotResponse>>> getFilteredSpots(
            @AuthenticationPrincipal CustomUserDetails user,
            @ModelAttribute SpotGetFilteredRequest req,
            @PageableDefault(sort = "createdAt",direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(spotService.getFilteredSpots(user.getId(), req,pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpotDetailResponse>> getSpotById(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id){
        return  ResponseEntity.ok(ApiResponse.success(spotService.getSpotById(user.getId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SpotResponse>> registerSpot(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody SpotCreateRequest req){
        return  ResponseEntity.ok(ApiResponse.success(spotService.createSpot(user.getId(),req)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<SpotResponse>> updateSpot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @RequestBody SpotUpdateRequest req){
        return  ResponseEntity.ok(ApiResponse.success(spotService.updateSpot(user.getId(), id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSpot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        spotService.deleteSpot(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/test/renew/{placeId}")
    public ResponseEntity<ApiResponse<Void>> spotDataUpdate(
            @RequestBody SpotCreateRequest req,
            @PathVariable String placeId
    ){
        spotService.spotDataUpdate(placeId,req);
        return  ResponseEntity.ok(ApiResponse.success(null));
    }

}