package study.yume.security;

import org.junit.jupiter.api.Test;
import study.yume.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitServiceTests {

    private final AuthRateLimitService service = new AuthRateLimitService(
            Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void blocksLoginAfterFiveFailuresForSameClientAndUser() {
        for (int count = 0; count < 4; count++) {
            service.recordLoginFailure("127.0.0.1", "testuser");
        }

        assertThatThrownBy(() -> service.recordLoginFailure("127.0.0.1", "testuser"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("15분");
        assertThatThrownBy(() -> service.checkLoginAllowed("127.0.0.1", "testuser"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void successfulLoginClearsFailureCount() {
        for (int count = 0; count < 4; count++) {
            service.recordLoginFailure("127.0.0.1", "testuser");
        }

        service.recordLoginSuccess("127.0.0.1", "testuser");

        assertThatCode(() -> service.checkLoginAllowed("127.0.0.1", "testuser"))
                .doesNotThrowAnyException();
    }

    @Test
    void limitsSignupRequestsPerClient() {
        for (int count = 0; count < 5; count++) {
            service.consumeSignupAttempt("127.0.0.1");
        }

        assertThatThrownBy(() -> service.consumeSignupAttempt("127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
