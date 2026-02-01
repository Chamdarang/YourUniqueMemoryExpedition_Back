package study.yume.dto.spotpurchase.response;

import study.yume.model.SpotPurchase;
import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpotPurchaseResponse (
    Long id,
    Long spotId,
    String spotName,
    PurchaseKind kind,
    String category,
    String itemName,
    PurchaseStatus status,
    Integer quantity,
    BigDecimal price,
    String currency,
    LocalDate acquiredDate,
    String note
){
    public static SpotPurchaseResponse toDto(SpotPurchase spotPurchase) {
        return new SpotPurchaseResponse(
                spotPurchase.getId(),
                spotPurchase.getSpot().getId(),
                spotPurchase.getSpot().getSpotName(),
                spotPurchase.getKind(),
                spotPurchase.getCategory(),
                spotPurchase.getItemName(),
                spotPurchase.getStatus(),
                spotPurchase.getQuantity(),
                spotPurchase.getPrice(),
                spotPurchase.getCurrency(),
                spotPurchase.getAcquiredDate(),
                spotPurchase.getNote()
        );
    }
}
