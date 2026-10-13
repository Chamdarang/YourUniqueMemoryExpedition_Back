package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.ApiResponse;
import study.yume.security.CustomUserDetails;
import study.yume.service.CollectionPhotoService;
import java.io.IOException;

@RestController
@RequestMapping("/api/goshuin")
@RequiredArgsConstructor
public class CollectionPhotoController {
    private final CollectionPhotoService service;
    @PostMapping(value="/{type:books|records}/{id}/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> upload(@AuthenticationPrincipal CustomUserDetails u,@PathVariable String type,@PathVariable Long id,@RequestParam Long version,@RequestPart MultipartFile file) throws IOException {
        return ApiResponse.success(service.upload(u.getId(),id,type.equals("books"),version,file));
    }
    @DeleteMapping("/{type:books|records}/{id}/photo")
    public ApiResponse<Void> remove(@AuthenticationPrincipal CustomUserDetails u,@PathVariable String type,@PathVariable Long id,@RequestParam Long version) {
        service.remove(u.getId(),id,type.equals("books"),version);return ApiResponse.success(null);
    }
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal CustomUserDetails u,@PathVariable String id,@RequestParam(defaultValue="false") boolean thumbnail) throws IOException {
        var file=service.get(u.getId(),id,thumbnail);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.type())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
}
