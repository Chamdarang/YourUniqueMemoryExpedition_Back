package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import study.yume.model.SpotPurchase;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotPurchaseRepository extends JpaRepository<SpotPurchase, Long> {
    Optional<SpotPurchase> findByUserIdAndId(Long userId, Long id);
    List<SpotPurchase> findAllByUserIdAndSpotUserId(Long userId, Long spotUserId);
}
