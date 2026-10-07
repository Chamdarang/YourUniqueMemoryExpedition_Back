package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanLiveUpdateService;

@RestController
@RequiredArgsConstructor
public class PlanLiveUpdateController {
    private final PlanLiveUpdateService planLiveUpdateService;

    @GetMapping(value = "/api/plans/{planId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long planId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(planLiveUpdateService.subscribe(user.getId(), planId));
    }
}
