package study.yume.repository;

import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.model.Spot;
import study.yume.model.enums.SpotType;

import java.util.Optional;

@Repository
public interface SpotRepository extends JpaRepository<Spot,Long> {
    Optional<Spot> findByUserIdAndId(Long userId, Long spotId);
    boolean existsByUserIdAndLocationAndSpotName(Long userId,Point location, String spotName); //직접 등록한 장소의 경우(placeId없음)
    boolean existsByUserIdAndPlaceId(Long userId, String placeId); //구글맵 검색결과로 등록한 장소

    //리스트 조회
    @Query("SELECT s FROM Spot s WHERE s.userId = :userId " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "(s.spotName LIKE CONCAT('%', :keyword, '%') OR " +
            "s.description LIKE CONCAT('%', :keyword, '%'))) " +

            "AND (:spotType IS NULL OR s.spotType = :spotType) " +
            "AND (:isVisit IS NULL OR s.isVisit = :isVisit)")
    Page<Spot> searchMySpots(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("spotType") SpotType spotType,
            @Param("isVisit") Boolean isVisit,
            Pageable pageable);

    //지도 조회(위치 기반)
    @Query(value = "SELECT * FROM spot s WHERE s.user_id = :userId " +
            "AND (:isVisit IS NULL OR s.is_visit = :isVisit) " +
            "AND (:spotType IS NULL OR s.spot_type = :#{#spotType?.name()}) " +
            "AND ST_Distance_Sphere(s.location, :center) <= :radius",
            nativeQuery = true)
    Page<Spot> findSpotsWithFilters(
            @Param("userId") Long userId,
            @Param("isVisit") Boolean isVisit,
            @Param("spotType") SpotType spotType,
            @Param("center") Point center,
            @Param("radius") double radius,
            Pageable pageable
    );

    @Query("SELECT s FROM Spot s " +
            "LEFT JOIN FETCH s.spotPurchases " +
            "WHERE s.id = :spotId AND s.userId = :userId")
    Optional<Spot> findDetailByUserIdAndId(@Param("userId") Long userId, @Param("spotId") Long spotId);
}
