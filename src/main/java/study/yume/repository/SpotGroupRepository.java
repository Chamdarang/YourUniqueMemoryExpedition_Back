package study.yume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import study.yume.model.SpotGroup;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotGroupRepository extends JpaRepository<SpotGroup, Long> {
    List<SpotGroup> findAllByUserId(Long userId);
    Optional<SpotGroup> findByUserIdAndId(Long userId, Long groupId);
}
