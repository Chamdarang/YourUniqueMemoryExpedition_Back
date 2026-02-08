package study.yume.dto.spotpurchase.request;

import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

public record SpotPurchaseSearchRequest(
        String keyword,
        PurchaseKind kind,
        PurchaseStatus status,
        String category
) {}