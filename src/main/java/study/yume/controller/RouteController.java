package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import study.yume.dto.ApiResponse;
import study.yume.dto.route.request.RouteEstimateRequest;
import study.yume.dto.route.response.RouteEstimateResponse;
import study.yume.service.RouteEstimateService;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteEstimateService routeEstimateService;

    @PostMapping("/estimate")
    public ResponseEntity<ApiResponse<RouteEstimateResponse>> estimate(
            @RequestBody RouteEstimateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(routeEstimateService.estimate(request)));
    }
}
