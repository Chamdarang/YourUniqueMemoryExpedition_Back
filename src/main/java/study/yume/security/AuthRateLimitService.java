package study.yume.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import study.yume.exception.RateLimitExceededException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
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

    private static final RedisScript<Long> CHECK_LOGIN_SCRIPT = new DefaultRedisScript<>("""
            local blockedUntil = redis.call('GET', KEYS[2])
            if blockedUntil then
              if tonumber(blockedUntil) > tonumber(ARGV[1]) then
                return tonumber(blockedUntil) - tonumber(ARGV[1])
              end
              redis.call('DEL', KEYS[1], KEYS[2])
            end
            return 0
            """, Long.class);

    private static final RedisScript<Long> RECORD_LOGIN_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if failures >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
              return tonumber(ARGV[4])
            end
            return 0
            """, Long.class);

    private static final RedisScript<Long> CONSUME_SIGNUP_SCRIPT = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if attempts > tonumber(ARGV[2]) then
              redis.call('DECR', KEYS[1])
              local ttl = redis.call('PTTL', KEYS[1])
              return ttl
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Clock clock;

    // The in-memory path is retained only for deterministic unit tests that inject a Clock.
    private final ConcurrentMap<String, LoginState> testLoginStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SignupState> testSignupStates = new ConcurrentHashMap<>();
    private final AtomicInteger testOperationCount = new AtomicInteger();

    @Autowired
    public AuthRateLimitService(
            StringRedisTemplate redis,
            @Value("${auth.rate-limit.redis-key-prefix:yume:auth:}") String keyPrefix
    ) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.clock = Clock.systemUTC();
    }

    AuthRateLimitService(Clock clock) {
        this.redis = null;
        this.keyPrefix = "yume:auth:";
        this.clock = clock;
    }

    public void checkLoginAllowed(String clientAddress, String username) {
        if (redis == null) {
            checkLoginAllowedInMemory(clientAddress, username);
            return;
        }
        long now = clock.millis();
        String baseKey = loginBaseKey(clientAddress, username);
        Long retryAfter = redis.execute(
                CHECK_LOGIN_SCRIPT,
                List.of(baseKey + ":failures", baseKey + ":blocked"),
                Long.toString(now)
        );
        if (retryAfter != null && retryAfter > 0) {
            throw limitExceeded("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
    }

    public void recordLoginFailure(String clientAddress, String username) {
        if (redis == null) {
            recordLoginFailureInMemory(clientAddress, username);
            return;
        }
        long now = clock.millis();
        String baseKey = loginBaseKey(clientAddress, username);
        Long blockMillis = redis.execute(
                RECORD_LOGIN_FAILURE_SCRIPT,
                List.of(baseKey + ":failures", baseKey + ":blocked"),
                Long.toString(LOGIN_WINDOW_MILLIS),
                Integer.toString(MAX_LOGIN_FAILURES),
                Long.toString(now + LOGIN_BLOCK_MILLIS),
                Long.toString(LOGIN_BLOCK_MILLIS)
        );
        if (blockMillis != null && blockMillis > 0) {
            throw limitExceeded("로그인에 여러 번 실패하여 15분 동안 로그인이 제한됩니다.", blockMillis);
        }
    }

    public void recordLoginSuccess(String clientAddress, String username) {
        if (redis == null) {
            testLoginStates.remove(loginKey(clientAddress, username));
            return;
        }
        String baseKey = loginBaseKey(clientAddress, username);
        redis.delete(List.of(baseKey + ":failures", baseKey + ":blocked"));
    }

    public void consumeSignupAttempt(String clientAddress) {
        if (redis == null) {
            consumeSignupAttemptInMemory(clientAddress);
            return;
        }
        Long retryAfter = redis.execute(
                CONSUME_SIGNUP_SCRIPT,
                List.of(keyPrefix + "signup:" + hashed(normalizeClientAddress(clientAddress))),
                Long.toString(SIGNUP_WINDOW_MILLIS),
                Integer.toString(MAX_SIGNUPS)
        );
        if (retryAfter != null && retryAfter > 0) {
            throw limitExceeded("회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
    }

    private String loginBaseKey(String clientAddress, String username) {
        return keyPrefix + "login:" + hashed(loginKey(clientAddress, username));
    }

    private String loginKey(String clientAddress, String username) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizeClientAddress(clientAddress) + ":" + normalizedUsername;
    }

    private String normalizeClientAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
    }

    private String hashed(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private RateLimitExceededException limitExceeded(String message, long retryAfterMillis) {
        long seconds = Math.max(1, (retryAfterMillis + 999) / 1000);
        return new RateLimitExceededException(message, seconds);
    }

    private void checkLoginAllowedInMemory(String clientAddress, String username) {
        long now = clock.millis();
        String key = loginKey(clientAddress, username);
        LoginState state = testLoginStates.get(key);
        if (state == null) {
            cleanupTestState(now);
            return;
        }
        synchronized (state) {
            if (state.blockedUntil > now) {
                throw limitExceeded("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", state.blockedUntil - now);
            }
            if (state.blockedUntil > 0 || now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS) {
                testLoginStates.remove(key, state);
            }
        }
        cleanupTestState(now);
    }

    private void recordLoginFailureInMemory(String clientAddress, String username) {
        long now = clock.millis();
        String key = loginKey(clientAddress, username);
        LoginState state = testLoginStates.computeIfAbsent(key, ignored -> new LoginState(now));
        synchronized (state) {
            if (now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS) {
                state.windowStartedAt = now;
                state.failures = 0;
                state.blockedUntil = 0;
            }
            state.failures++;
            if (state.failures >= MAX_LOGIN_FAILURES) {
                state.blockedUntil = now + LOGIN_BLOCK_MILLIS;
                throw limitExceeded("로그인에 여러 번 실패하여 15분 동안 로그인이 제한됩니다.", LOGIN_BLOCK_MILLIS);
            }
        }
        cleanupTestState(now);
    }

    private void consumeSignupAttemptInMemory(String clientAddress) {
        long now = clock.millis();
        String key = normalizeClientAddress(clientAddress);
        SignupState state = testSignupStates.computeIfAbsent(key, ignored -> new SignupState(now));
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
        cleanupTestState(now);
    }

    private void cleanupTestState(long now) {
        if ((testOperationCount.incrementAndGet() & 255) != 0) return;
        testLoginStates.entrySet().removeIf(entry -> {
            LoginState state = entry.getValue();
            synchronized (state) {
                return state.blockedUntil <= now && now - state.windowStartedAt >= LOGIN_WINDOW_MILLIS;
            }
        });
        testSignupStates.entrySet().removeIf(entry -> {
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
        private LoginState(long windowStartedAt) { this.windowStartedAt = windowStartedAt; }
    }

    private static final class SignupState {
        private long windowStartedAt;
        private int attempts;
        private SignupState(long windowStartedAt) { this.windowStartedAt = windowStartedAt; }
    }
}
