package study.yume.dto.goshuin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class GoshuinDto {
    private GoshuinDto() {}
    public record Book(Long id, String name, String memo, String photoId,
                       Long sourcePurchaseId, long version, int count) {}
    public record BookInput(String name, String memo, Long sourcePurchaseId, Long version) {}
    public record Entry(Long id, Long spotUserId, String placeName, String itemName,
                        LocalDate acquiredDate, String note, Long bookId, String bookName,
                        Integer position, String photoId, long version, int quantity) {}
    public record EntryInput(String placeName, Long spotUserId, String itemName,
                             LocalDate acquiredDate, String note, Long bookId,
                             Integer position, Long version) {}
    public record OrderInput(List<Long> ids, Long version) {}
    public record ImportInput(List<Long> ids, Long version, Map<Long,Long> recordVersions) {}
    public record Entries(List<Entry> content, long totalElements, int number, int totalPages) {}
    public record Stats(long unboundCount) {}
    public record PurchaseSource(Long id, String name, Long bookId) {}
}
