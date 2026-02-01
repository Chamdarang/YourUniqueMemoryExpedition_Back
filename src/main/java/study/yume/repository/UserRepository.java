package study.yume.repository;

import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jpa.repository.JpaRepository;
import study.yume.model.User;

import java.util.Optional;

@ReadingConverter
public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
}
