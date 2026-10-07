package study.yume.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.account.request.ChangePasswordRequest;
import study.yume.dto.account.request.DeleteAccountRequest;
import study.yume.dto.account.request.UpdateNicknameRequest;
import study.yume.dto.account.response.AccountProfileResponse;
import study.yume.model.User;
import study.yume.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public AccountProfileResponse getProfile(Long userId) {
        return toProfile(getUser(userId));
    }

    public AccountProfileResponse updateNickname(Long userId, UpdateNicknameRequest request) {
        if (request == null || request.nickname() == null) {
            throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        }
        String nickname = request.nickname().trim();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > 100) {
            throw new IllegalArgumentException("닉네임은 1~100자로 입력해 주세요.");
        }

        User user = getUser(userId);
        user.setNickname(nickname);
        return toProfile(userRepository.save(user));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (request == null || isBlank(request.currentPassword()) || request.newPassword() == null) {
            throw new IllegalArgumentException("현재 비밀번호와 새 비밀번호를 입력해 주세요.");
        }
        User user = getUser(userId);
        verifyPassword(user, request.currentPassword());

        String newPassword = request.newPassword();
        if (newPassword.length() < 4 || newPassword.length() > 72) {
            throw new IllegalArgumentException("새 비밀번호는 4~72자로 입력해 주세요.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        if (request == null || isBlank(request.password())) {
            throw new IllegalArgumentException("계정 삭제를 위해 현재 비밀번호를 입력해 주세요.");
        }
        User user = getUser(userId);
        verifyPassword(user, request.password());

        // Preserve copied schedule snapshots owned by other accounts while removing private spot references.
        jdbcTemplate.update("UPDATE day_schedule SET spot_user_id = NULL WHERE spot_user_id IN (SELECT id FROM spot_user WHERE user_id = ?)", userId);
        jdbcTemplate.update("UPDATE spot_purchase SET spot_user_id = NULL WHERE user_id <> ? AND spot_user_id IN (SELECT id FROM spot_user WHERE user_id = ?)", userId, userId);

        jdbcTemplate.update("DELETE FROM spot_visit_history WHERE user_id = ? OR spot_user_id IN (SELECT id FROM spot_user WHERE user_id = ?)", userId, userId);
        jdbcTemplate.update("DELETE FROM schedule_visit_status WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM day_schedule WHERE user_id = ? OR plan_day_id IN (SELECT id FROM plan_day WHERE user_id = ?)", userId, userId);
        jdbcTemplate.update("DELETE FROM plan_day WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM plan WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM spot_purchase WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM spot_group_map WHERE group_id IN (SELECT id FROM spot_group WHERE user_id = ?) OR spot_user_id IN (SELECT id FROM spot_user WHERE user_id = ?)", userId, userId);
        jdbcTemplate.update("DELETE FROM spot_group WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM spot_user WHERE user_id = ?", userId);

        userRepository.delete(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    }

    private void verifyPassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
    }

    private AccountProfileResponse toProfile(User user) {
        String nickname = user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername()
                : user.getNickname();
        return new AccountProfileResponse(user.getUsername(), nickname);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
