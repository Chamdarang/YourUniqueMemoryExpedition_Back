package study.yume.dto.spotpurchase.request;

import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseUpdateRequest(
        Long spotUserId,
        PurchaseKind kind,
        String category,
        String itemName,
        PurchaseStatus status,
        Integer quantity,
        BigDecimal price,
        String currency,
        LocalDate acquiredDate,
        String note

) {
}
