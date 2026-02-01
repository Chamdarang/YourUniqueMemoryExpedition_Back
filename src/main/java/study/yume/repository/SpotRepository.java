package study.yume.repository;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.model.Spot;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotRepository extends JpaRepository<Spot,Long> {
    List<Spot> findALLByUserId(Long userId);
    List<Spot> findByUserIdAndSpotNameContains(Long userId, String name);
    List<Spot> findAllByUserIdAndIsVisit(Long userId, Boolean isVisit);
    Optional<Spot> findByUserIdAndId(Long userId, Long spotId);
    boolean existsByUserIdAndLocationAndSpotName(Long userId,Point location, String spotName); //직접 등록한 장소의 경우(placeId없음)
    boolean existsByUserIdAndPlaceId(Long userId, String placeId); //구글맵 검색결과로 등록한 장소


    @Query(value = "SELECT * FROM spot s WHERE s.user_id = :userId " +
            "AND (:isVisit IS NULL OR s.is_visit = :isVisit) " +
            "AND ST_Distance_Sphere(s.location, :center) <= :radius",
            nativeQuery = true)
    List<Spot> findSpotsWithFilters(
            @Param("userId") Long userId,
            @Param("isVisit") Boolean isVisit,
            @Param("center") Point center,
            @Param("radius") double radius
    );

    @Query("SELECT s FROM Spot s " +
            "LEFT JOIN FETCH s.spotPurchases " +
            "WHERE s.id = :spotId AND s.userId = :userId")
    Optional<Spot> findDetailByUserIdAndId(@Param("userId") Long userId, @Param("spotId") Long spotId);
}
