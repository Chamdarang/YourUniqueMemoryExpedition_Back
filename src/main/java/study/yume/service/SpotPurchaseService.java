package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.spotpurchase.request.PurchaseCreateRequest;
import study.yume.dto.spotpurchase.request.PurchaseUpdateRequest;
import study.yume.dto.spotpurchase.response.SpotPurchaseResponse;
import study.yume.model.Spot;
import study.yume.model.SpotPurchase;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotRepository;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SpotPurchaseService {
    private final SpotPurchaseRepository spotPurchaseRepository;
    private final SpotRepository spotRepository;


    public SpotPurchaseResponse createPurchase(Long userId, PurchaseCreateRequest req){
        Spot spot = findSpotByUserIdAndId(userId, req.spotId());

        SpotPurchase spotPurchase = new SpotPurchase();
        spotPurchase.setUserId(userId);
        spotPurchase.setSpot(spot);
        spotPurchase.setKind(req.kind());
        spotPurchase.setCategory(req.category());
        spotPurchase.setItemName(req.itemName());
        spotPurchase.setStatus(req.status());
        spotPurchase.setQuantity(req.quantity()!=null?req.quantity():1);
        spotPurchase.setPrice(req.price());
        spotPurchase.setCurrency(req.currency());
        spotPurchase.setAcquiredDate(req.acquiredDate());
        spotPurchase.setNote(req.note());

        return SpotPurchaseResponse.toDto(spotPurchaseRepository.save(spotPurchase));
    }

    public List<SpotPurchaseResponse> findAllBySpotId(Long userId,Long spotId){
        findSpotByUserIdAndId(userId, spotId);
        return spotPurchaseRepository.findAllByUserIdAndSpotId(userId,spotId).stream()
                .map(SpotPurchaseResponse::toDto)
                .toList();
    }

    public SpotPurchaseResponse updatePurchase(Long userId, Long purchaseId, PurchaseUpdateRequest req){
        SpotPurchase purchase = findPurchaseByUserIdAndId(userId,purchaseId);

        if (req.kind() != null) purchase.setKind(req.kind());
        if (req.category() != null) purchase.setCategory(req.category());
        if (req.itemName() != null) purchase.setItemName(req.itemName());
        if (req.status() != null) purchase.setStatus(req.status());
        if (req.quantity() != null) purchase.setQuantity(req.quantity());
        if (req.price() != null) purchase.setPrice(req.price());
        if (req.currency() != null) purchase.setCurrency(req.currency());
        if (req.acquiredDate() != null) purchase.setAcquiredDate(req.acquiredDate());
        if (req.note() != null) purchase.setNote(req.note());

        return SpotPurchaseResponse.toDto(spotPurchaseRepository.save(purchase));
    }

    public void deletePurchase(Long userId, Long purchaseId){
        SpotPurchase purchase = findPurchaseByUserIdAndId(userId,purchaseId);

        spotPurchaseRepository.delete(purchase);
    }


    private SpotPurchase findPurchaseByUserIdAndId(Long userId, Long purchaseId) {
        return spotPurchaseRepository.findByUserIdAndId(userId, purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("해당 물품을 찾을 수 없습니다."));
    }

    private Spot findSpotByUserIdAndId(Long userId, Long spotId) {
        return spotRepository.findByUserIdAndId(userId, spotId)
                .orElseThrow(() -> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
    }
}
