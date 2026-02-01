package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.SpotPurchase;

import java.util.List;
import java.util.Optional;

public interface SpotPurchaseRepository extends JpaRepository<SpotPurchase, Long> {
    Optional<SpotPurchase> findByUserIdAndId(Long userId, Long id);
    List<SpotPurchase> findAllByUserIdAndSpotId(Long userId, Long spotId);
}
