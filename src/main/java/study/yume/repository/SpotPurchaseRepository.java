package study.yume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.model.SpotPurchase;
import study.yume.model.SpotUser;
import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotPurchaseRepository extends JpaRepository<SpotPurchase, Long> {
    Optional<SpotPurchase> findByUserIdAndId(Long userId, Long id);
    List<SpotPurchase> findAllByUserIdAndSpotUserId(Long userId, Long spotUserId);

    @Query("SELECT sp FROM SpotPurchase sp " +
            "WHERE sp.userId = :userId " +
            "AND (:kind IS NULL OR sp.kind = :kind) " +
            "AND (:status IS NULL OR sp.status = :status) " +
            "AND (:category IS NULL OR sp.category = :category) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "    (sp.itemName LIKE CONCAT('%', :keyword, '%') OR " +
            "     sp.note LIKE CONCAT('%', :keyword, '%')))")
    Page<SpotPurchase> searchPurchases(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("kind") PurchaseKind kind,
            @Param("status") PurchaseStatus status,
            @Param("category") String category,
            Pageable pageable);

    @Modifying
    @Query("UPDATE SpotPurchase sp SET sp.spotUser = :target " +
            "WHERE sp.userId = :userId AND sp.spotUser.id = :sourceId")
    int reassignSpotUser(
            @Param("userId") Long userId,
            @Param("sourceId") Long sourceId,
            @Param("target") SpotUser target
    );
}
