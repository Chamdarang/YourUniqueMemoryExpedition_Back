package study.yume.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import study.yume.dto.auth.request.SignupRequest;
import study.yume.model.User;
import study.yume.model.enums.Role;
import study.yume.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CustomUserDetailsService service =
            new CustomUserDetailsService(userRepository, passwordEncoder);

    @Test
    void registersIdAndPasswordWithBcryptAndUserRole() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("test-password")).thenReturn("$2a$10$hashed");

        service.register(new SignupRequest("testuser", "test-password"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(savedUser.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void rejectsShortCredentials() {
        assertThatThrownBy(() -> service.register(new SignupRequest("id", "pw")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
