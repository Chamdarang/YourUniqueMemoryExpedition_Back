package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.spotpurchase.request.PurchaseCreateRequest;
import study.yume.dto.spotpurchase.request.PurchaseUpdateRequest;
import study.yume.dto.spotpurchase.response.SpotPurchaseResponse;
import study.yume.model.SpotPurchase;
import study.yume.model.SpotUser;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotUserRepository;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SpotPurchaseService {
    private final SpotPurchaseRepository spotPurchaseRepository;
    private final SpotUserRepository spotUserRepository;


    public SpotPurchaseResponse createPurchase(Long userId, Long spotUserId, PurchaseCreateRequest req){
        SpotUser spotUser = findSpotUserByUserIdAndId(userId, spotUserId);

        SpotPurchase spotPurchase = new SpotPurchase();
        spotPurchase.setUserId(userId);
        spotPurchase.setSpotUser(spotUser);
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

    public List<SpotPurchaseResponse> findAllBySpotUserId(Long userId,Long spotUserId){
        findSpotUserByUserIdAndId(userId, spotUserId);
        return spotPurchaseRepository.findAllByUserIdAndSpotUserId(userId,spotUserId).stream()
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

    private SpotUser findSpotUserByUserIdAndId(Long userId, Long spotUserId) {
        return spotUserRepository.findByUserIdAndId(userId, spotUserId)
                .orElseThrow(() -> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
    }
}
