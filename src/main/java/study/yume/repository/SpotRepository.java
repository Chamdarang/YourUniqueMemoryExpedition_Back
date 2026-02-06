package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import study.yume.model.Spot;
import java.util.Optional;

@Repository
public interface SpotRepository extends JpaRepository<Spot,Long> {
    Optional<Spot> findByPlaceId(String placeId);

}
