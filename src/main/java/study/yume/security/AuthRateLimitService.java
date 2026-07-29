package study.yume.security;

import org.springframework.stereotype.Service;
import study.yume.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthRateLimitService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_WINDOW_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long LOGIN_BLOCK_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final int MAX_SIGNUPS = 5;
    private static final long SIGNUP_WINDOW_MILLIS = Duration.ofHours(1).toMillis();

    private final ConcurrentMap<String, LoginState> loginStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SignupState> signupStates = new ConcurrentHashMap<>();
    private final AtomicInteger operationCount = new AtomicInteger();
    private final Clock clock;

    public AuthRateLimitService() {
        this(Clock.systemUTC());
    }

    AuthRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void checkLoginAllowed(String clientAddress, String username) {
        long now = clock.millis();
        String key = loginKey(clientAddress, username);
        LoginState state = loginStates.get(key);
        if (state == null) {
            cleanupPeriodically(now);
            return;
        }

        synchronized (state) {
            if (state.blockedUntil > now) {
                throw limitExceeded("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", state.blockedUntil - now);
            }
            if (state.blockedUntil > 0 || now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS) {
                loginStates.remove(key, state);
            }
        }
        cleanupPeriodically(now);
    }

    public void recordLoginFailure(String clientAddress, String username) {
        long now = clock.millis();
        String key = loginKey(clientAddress, username);
        LoginState state = loginStates.computeIfAbsent(key, ignored -> new LoginState(now));

        synchronized (state) {
            if (now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS) {
                state.windowStartedAt = now;
                state.failures = 0;
                state.blockedUntil = 0;
            }
            state.failures++;
            if (state.failures >= MAX_LOGIN_FAILURES) {
                state.blockedUntil = now + LOGIN_BLOCK_MILLIS;
                throw limitExceeded(
                        "로그인에 여러 번 실패하여 15분 동안 로그인이 제한됩니다.",
                        LOGIN_BLOCK_MILLIS
                );
            }
        }
        cleanupPeriodically(now);
    }

    public void recordLoginSuccess(String clientAddress, String username) {
        loginStates.remove(loginKey(clientAddress, username));
    }

    public void consumeSignupAttempt(String clientAddress) {
        long now = clock.millis();
        String key = normalizeClientAddress(clientAddress);
        SignupState state = signupStates.computeIfAbsent(key, ignored -> new SignupState(now));

        synchronized (state) {
            if (now - state.windowStartedAt >= SIGNUP_WINDOW_MILLIS) {
                state.windowStartedAt = now;
                state.attempts = 0;
            }
            if (state.attempts >= MAX_SIGNUPS) {
                long retryAfter = SIGNUP_WINDOW_MILLIS - (now - state.windowStartedAt);
                throw limitExceeded("회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
            }
            state.attempts++;
        }
        cleanupPeriodically(now);
    }

    private String loginKey(String clientAddress, String username) {
        String normalizedUsername = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
        return normalizeClientAddress(clientAddress) + ":" + normalizedUsername;
    }

    private String normalizeClientAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }

    private RateLimitExceededException limitExceeded(String message, long retryAfterMillis) {
        long seconds = Math.max(1, (retryAfterMillis + 999) / 1000);
        return new RateLimitExceededException(message, seconds);
    }

    private void cleanupPeriodically(long now) {
        if ((operationCount.incrementAndGet() & 255) != 0) {
            return;
        }
        loginStates.entrySet().removeIf(entry -> {
            LoginState state = entry.getValue();
            synchronized (state) {
                return state.blockedUntil <= now
                        && now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS;
            }
        });
        signupStates.entrySet().removeIf(entry -> {
            SignupState state = entry.getValue();
            synchronized (state) {
                return now - state.windowStartedAt >= SIGNUP_WINDOW_MILLIS;
            }
        });
    }

    private static final class LoginState {
        private long windowStartedAt;
        private int failures;
        private long blockedUntil;

        private LoginState(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }

    private static final class SignupState {
        private long windowStartedAt;
        private int attempts;

        private SignupState(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
