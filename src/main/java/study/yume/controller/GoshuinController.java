package study.yume.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import study.yume.dto.ApiResponse;
import study.yume.dto.goshuin.GoshuinDto.*;
import study.yume.security.CustomUserDetails;
import study.yume.service.GoshuinService;
import java.util.List;

@RestController
@RequestMapping("/api/goshuin")
@RequiredArgsConstructor
public class GoshuinController {
    private final GoshuinService service;
    @GetMapping("/books") public ApiResponse<List<Book>> books(@AuthenticationPrincipal CustomUserDetails u){return ApiResponse.success(service.books(u.getId()));}
    @GetMapping("/stats") public ApiResponse<Stats> stats(@AuthenticationPrincipal CustomUserDetails u){return ApiResponse.success(service.stats(u.getId()));}
    @GetMapping("/books/from-purchase/{id}") public ApiResponse<PurchaseSource> source(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ApiResponse.success(service.purchaseSource(u.getId(),id));}
    @GetMapping("/books/{id}") public ApiResponse<Book> book(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ApiResponse.success(service.book(u.getId(),id));}
    @PostMapping("/books") public ApiResponse<Book> create(@AuthenticationPrincipal CustomUserDetails u,@RequestBody BookInput in){return ApiResponse.success(service.saveBook(u.getId(),null,in));}
    @PutMapping("/books/{id}") public ApiResponse<Book> update(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestBody BookInput in){return ApiResponse.success(service.saveBook(u.getId(),id,in));}
    @DeleteMapping("/books/{id}") public ApiResponse<Void> delete(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestParam(defaultValue="false") boolean deleteEntries,@RequestParam Long version){service.deleteBook(u.getId(),id,deleteEntries,version);return ApiResponse.success(null);}
    @GetMapping("/records") public ApiResponse<Entries> records(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(required=false) Long bookId,@RequestParam(defaultValue="false") boolean unbound,@RequestParam(required=false) String keyword,@RequestParam(defaultValue="0") int page){return ApiResponse.success(service.entries(u.getId(),bookId,unbound,keyword,page));}
    @GetMapping("/records/{id}") public ApiResponse<Entry> record(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ApiResponse.success(service.entry(u.getId(),id));}
    @PostMapping("/records") public ApiResponse<Entry> add(@AuthenticationPrincipal CustomUserDetails u,@RequestBody EntryInput in){return ApiResponse.success(service.saveEntry(u.getId(),null,in));}
    @PutMapping("/records/{id}") public ApiResponse<Entry> edit(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestBody EntryInput in){return ApiResponse.success(service.saveEntry(u.getId(),id,in));}
    @DeleteMapping("/records/{id}") public ApiResponse<Void> remove(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestParam Long version){service.deleteEntry(u.getId(),id,version);return ApiResponse.success(null);}
    @GetMapping("/books/{id}/order") public ApiResponse<List<Entry>> order(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ApiResponse.success(service.order(u.getId(),id));}
    @PutMapping("/books/{id}/order") public ApiResponse<Book> reorder(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestBody OrderInput in){return ApiResponse.success(service.reorder(u.getId(),id,in));}
    @PostMapping("/books/{id}/records") public ApiResponse<Book> importRecords(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestBody ImportInput in){return ApiResponse.success(service.importEntries(u.getId(),id,in));}
}
