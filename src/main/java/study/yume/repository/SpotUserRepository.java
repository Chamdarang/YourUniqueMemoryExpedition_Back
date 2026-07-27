package study.yume.repository;

import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import study.yume.model.SpotUser;
import study.yume.model.enums.SpotType;

import java.util.Optional;

@Repository
public interface SpotUserRepository extends JpaRepository<SpotUser, Long> {

    Optional<SpotUser> findByUserIdAndId(Long userId, Long id);
    Optional<SpotUser> findByUserIdAndSpotId(Long userId, Long spotId);
    boolean existsByUserIdAndSpotId(Long userId, Long spotId);

    @Query("SELECT su FROM SpotUser su JOIN su.spot s " +
            "WHERE su.userId = :userId " +
            "AND (:isVisit IS NULL OR su.isVisit = :isVisit) " +
            "AND (:spotType IS NULL OR su.spotType = :spotType) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "(s.spotName LIKE CONCAT('%', :keyword, '%') OR " +
            "su.customName LIKE CONCAT('%', :keyword, '%') OR " +
            "su.description LIKE CONCAT('%', :keyword, '%')))")
    Page<SpotUser> searchMySpotUsers(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("spotType") SpotType spotType,
            @Param("isVisit") Boolean isVisit,
            Pageable pageable);

    @Query(value = "SELECT su.* FROM spot_user su " +
            "JOIN spot s ON su.spot_id = s.id " +
            "WHERE su.user_id = :userId " +
            "AND (:isVisit IS NULL OR su.is_visit = :isVisit) " +
            "AND (:spotType IS NULL OR su.spot_type = :#{#spotType?.name()}) " +
            "AND ST_Distance_Sphere(s.location, :center) <= :radius",
            nativeQuery = true)
    Page<SpotUser> findWithLocation(
            @Param("userId") Long userId,
            @Param("isVisit") Boolean isVisit,
            @Param("spotType") SpotType spotType,
            @Param("center") Point center,
            @Param("radius") Double radius,
            Pageable pageable);

}
