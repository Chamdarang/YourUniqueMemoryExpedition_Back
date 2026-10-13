package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.spotpurchase.request.PurchaseCreateRequest;
import study.yume.dto.spotpurchase.request.PurchaseUpdateRequest;
import study.yume.dto.spotpurchase.request.SpotPurchaseSearchRequest;
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
    private final GoshuinService goshuinService;


    public SpotPurchaseResponse createPurchase(Long userId, Long spotUserId, PurchaseCreateRequest req){
        SpotUser spotUser = null;
        if (spotUserId!=0) spotUser = findSpotUserByUserIdAndId(userId, spotUserId);

        SpotPurchase spotPurchase = new SpotPurchase();
        spotPurchase.setUserId(userId);
        spotPurchase.setSpotUser(spotUser);
        if (req.kind() == study.yume.model.enums.PurchaseKind.GOSHUIN && spotUser != null)
            spotPurchase.setReceivedPlaceName(spotUser.getCustomName());
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

    @Transactional(readOnly = true)
    public Page<SpotPurchaseResponse> searchPurchases(Long userId, SpotPurchaseSearchRequest req, Pageable pageable) {
        return spotPurchaseRepository.searchPurchases(
                userId,
                req.keyword(),
                req.kind(),
                req.status(),
                req.category(),
                Boolean.TRUE.equals(req.unbound()),
                pageable
        ).map(SpotPurchaseResponse::toDto);
    }

    @Transactional(readOnly = true)
    public List<SpotPurchaseResponse> findAllBySpotUserId(Long userId,Long spotUserId){
        findSpotUserByUserIdAndId(userId, spotUserId);
        return spotPurchaseRepository.findAllByUserIdAndSpotUserId(userId,spotUserId).stream()
                .map(SpotPurchaseResponse::toDto)
                .toList();
    }

    public SpotPurchaseResponse updatePurchase(Long userId, Long purchaseId, PurchaseUpdateRequest req){
        goshuinService.lockOwner(userId);
        SpotPurchase purchase = findPurchaseByUserIdAndId(userId,purchaseId);

        if (purchase.getKind() == study.yume.model.enums.PurchaseKind.GOSHUIN || req.recordVersion() != null)
            GoshuinService.checkVersion(req.recordVersion(), purchase.getRecordVersion());

        if (purchase.getGoshuinBookId() != null &&
            ((req.kind() != null && req.kind() != study.yume.model.enums.PurchaseKind.GOSHUIN) ||
             (req.status() != null && req.status() != study.yume.model.enums.PurchaseStatus.ACQUIRED)))
            throw new IllegalArgumentException("먼저 고슈인첩에서 기록을 빼 주세요.");

        if (req.spotUserId() != null){
            boolean placeChanged = purchase.getSpotUser() == null || !purchase.getSpotUser().getId().equals(req.spotUserId());
            SpotUser spotUser = req.spotUserId() == 0 ? null : findSpotUserByUserIdAndId(userId, req.spotUserId());
            purchase.setSpotUser(spotUser);
            if (spotUser != null && (placeChanged || purchase.getReceivedPlaceName() == null) && purchase.getKind() == study.yume.model.enums.PurchaseKind.GOSHUIN)
                purchase.setReceivedPlaceName(spotUser.getCustomName());
        }
        if (req.kind() != null) purchase.setKind(req.kind());
        if (req.category() != null) purchase.setCategory(req.category());
        if (req.itemName() != null) purchase.setItemName(req.itemName());
        if (req.status() != null) purchase.setStatus(req.status());
        if (req.quantity() != null) purchase.setQuantity(req.quantity());
        if (req.price() != null) purchase.setPrice(req.price());
        if (req.currency() != null) purchase.setCurrency(req.currency());
        if (req.acquiredDate() != null) purchase.setAcquiredDate(req.acquiredDate());
        if (req.note() != null) purchase.setNote(req.note());

        purchase.setRecordVersion(purchase.getRecordVersion() + 1);
        goshuinService.legacyChanged(userId, purchase.getGoshuinBookId());
        return SpotPurchaseResponse.toDto(spotPurchaseRepository.save(purchase));
    }

    public void deletePurchase(Long userId, Long purchaseId){
        goshuinService.lockOwner(userId);
        SpotPurchase purchase = findPurchaseByUserIdAndId(userId,purchaseId);
        Long bookId = purchase.getGoshuinBookId();
        goshuinService.retirePhoto(purchase.getPhotoId());
        spotPurchaseRepository.delete(purchase);
        spotPurchaseRepository.flush();
        if (bookId != null) goshuinService.renumber(userId, bookId, null, null);
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
