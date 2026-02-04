package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import study.yume.service.MapService;

@RestController
@RequestMapping("/api/maps")
@RequiredArgsConstructor
public class MapController {
    private final MapService mapService;

    @GetMapping(value = "/static", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> staticMap(@RequestParam("query") String query) {
        if (query.contains("key=")) {
            return ResponseEntity.badRequest().build();
        }

        byte[] body = mapService.staticMap(query);
        if (body == null || body.length == 0) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // 캡처 안정성 위해 캐시 끔
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .body(body);

    }
}
