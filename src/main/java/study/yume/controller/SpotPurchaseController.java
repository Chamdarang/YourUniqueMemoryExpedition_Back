package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.spotpurchase.request.PurchaseCreateRequest;
import study.yume.dto.spotpurchase.request.PurchaseUpdateRequest;
import study.yume.dto.spotpurchase.response.SpotPurchaseResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.SpotPurchaseService;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class SpotPurchaseController {
    private final SpotPurchaseService spotPurchaseService;

    @PostMapping
    public ResponseEntity<ApiResponse<SpotPurchaseResponse>> createPurchase(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody PurchaseCreateRequest req){
        return ResponseEntity.ok(ApiResponse.success(spotPurchaseService.createPurchase(user.getId(),req)));
    }

    @GetMapping("/spot/{spotUserId}")
    public ResponseEntity<ApiResponse<List<SpotPurchaseResponse>>> findAllBySpotUserId(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long spotUserId){
        return ResponseEntity.ok(ApiResponse.success(spotPurchaseService.findAllBySpotUserId(user.getId(),spotUserId)));
    }

    @PatchMapping("/{purchaseId}")
    public ResponseEntity<ApiResponse<SpotPurchaseResponse>> updatePurchase(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long purchaseId,
            @RequestBody PurchaseUpdateRequest req){
        return ResponseEntity.ok(ApiResponse.success(spotPurchaseService.updatePurchase(user.getId(),purchaseId,req)));
    }

    @DeleteMapping("/{purchaseId}")
    public ResponseEntity<ApiResponse<Void>> deletePurchase(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long purchaseId){
        spotPurchaseService.deletePurchase(user.getId(),purchaseId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
