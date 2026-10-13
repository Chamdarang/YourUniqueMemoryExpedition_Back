package study.yume.dto.spotpurchase.response;

import study.yume.model.SpotPurchase;
import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpotPurchaseResponse (
    Long id,
    Long spotUserId,
    String spotName,
    PurchaseKind kind,
    String category,
    String itemName,
    PurchaseStatus status,
    Integer quantity,
    BigDecimal price,
    String currency,
    LocalDate acquiredDate,
    String note,
    Long goshuinBookId,
    Integer goshuinOrder,
    String photoId,
    long recordVersion
){
    public static SpotPurchaseResponse toDto(SpotPurchase spotPurchase) {
        return new SpotPurchaseResponse(
                spotPurchase.getId(),
                spotPurchase.getSpotUser() != null ? spotPurchase.getSpotUser().getId(): 0,
                spotPurchase.getReceivedPlaceName() != null ? spotPurchase.getReceivedPlaceName() :
                    spotPurchase.getSpotUser() != null ? spotPurchase.getSpotUser().getCustomName() : "",
                spotPurchase.getKind(),
                spotPurchase.getCategory(),
                spotPurchase.getItemName(),
                spotPurchase.getStatus(),
                spotPurchase.getQuantity(),
                spotPurchase.getPrice(),
                spotPurchase.getCurrency(),
                spotPurchase.getAcquiredDate(),
                spotPurchase.getNote(),
                spotPurchase.getGoshuinBookId(),
                spotPurchase.getGoshuinOrder(),
                spotPurchase.getPhotoId(),
                spotPurchase.getRecordVersion()
        );
    }
}
