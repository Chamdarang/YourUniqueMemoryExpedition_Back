package study.yume.security;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTests {

    @Test
    void signsAndValidatesTokenWithConfiguredSecret() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef");
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        String token = jwtUtil.generateToken("testuser", expiry);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserName(token)).isEqualTo("testuser");
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtUtil("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
