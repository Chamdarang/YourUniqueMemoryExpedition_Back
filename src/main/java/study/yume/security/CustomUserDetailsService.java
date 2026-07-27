package study.yume.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import study.yume.dto.auth.request.SignupRequest;
import study.yume.model.User;
import study.yume.model.enums.Role;
import study.yume.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final @Lazy PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("유저 없음: " + username));
        return new CustomUserDetails(user);
    }

    public void register(SignupRequest req) {
        if (req == null || req.username() == null || req.password() == null) {
            throw new IllegalArgumentException("아이디와 비밀번호를 입력해 주세요.");
        }

        String username = req.username().trim();
        String password = req.password();

        if (!username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, 밑줄을 사용해 3~30자로 입력해 주세요.");
        }
        if (password.length() < 4 || password.length() > 72) {
            throw new IllegalArgumentException("비밀번호는 4~72자로 입력해 주세요.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 사용자입니다.");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

}
