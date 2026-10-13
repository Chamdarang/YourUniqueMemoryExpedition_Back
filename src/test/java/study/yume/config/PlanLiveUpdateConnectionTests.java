package study.yume.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import study.yume.controller.PlanLiveUpdateController;
import study.yume.controller.PlanShareController;
import study.yume.model.Plan;
import study.yume.model.PlanShare;
import study.yume.model.User;
import study.yume.repository.*;
import study.yume.security.CustomUserDetails;
import study.yume.service.PlanAccessService;
import study.yume.service.PlanLiveUpdateService;
import study.yume.service.PlanShareService;
import study.yume.service.PlanShareTokenCipher;
import study.yume.ssefixture.ConnectionProbe;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PlanLiveUpdateConnectionTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:sse-connections;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=true"
})
class PlanLiveUpdateConnectionTests {
    private static final String TOKEN = "A".repeat(43);
    @LocalServerPort int port;
    @Autowired HikariDataSource dataSource;
    @Autowired EntityManager entityManager;
    @Autowired PlanLiveUpdateService liveUpdates;
    @Autowired @Qualifier("planLiveUpdateHeartbeatScheduler") ThreadPoolTaskScheduler heartbeatScheduler;
    @Autowired @Qualifier("planLiveUpdateAuthorizationScheduler") ThreadPoolTaskScheduler authorizationScheduler;
    @MockitoBean PlanRepository plans;
    @MockitoBean PlanShareRepository shares;
    @MockitoBean PlanMemberRepository members;
    @MockitoBean PlanDayRepository days;
    @MockitoBean DayScheduleRepository schedules;
    @MockitoBean PlanShareTokenCipher cipher;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final List<InputStream> streams = new ArrayList<>();
    private PlanShare share;

    @BeforeEach
    void permissionsPerformRealTransactionalDatabaseReads() {
        Plan plan = new Plan();
        plan.setId(20L);
        plan.setUserId(1L);
        share = new PlanShare();
        share.setPlanId(20L);
        share.setTokenHash("test-hash");
        when(plans.findById(anyLong())).thenAnswer(invocation -> {
            entityManager.createNativeQuery("select 1").getSingleResult();
            return Optional.of(plan);
        });
        when(shares.findByTokenHash(anyString())).thenAnswer(invocation -> {
            entityManager.createNativeQuery("select 1").getSingleResult();
            return Optional.of(share);
        });
    }

    @AfterEach
    void closeStreams() throws Exception {
        liveUpdates.closePlan(20L);
        for (InputStream stream : streams) stream.close();
        streams.clear();
    }

    @Test
    void twelvePrivateAndPublicStreamsDoNotExhaustTwoDatabaseConnections() throws Exception {
        for (int i = 0; i < 12; i++) {
            openStream(i % 2 == 0 ? "/api/plans/20/events" : "/api/public/plans/share/events");
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero());
        }
        assertThat(subscriptionCount()).isEqualTo(12);
        assertThat(read("/probe")).isEqualTo("true"); // Ordinary requests still have OSIV.
        assertThat(dataSource.getHikariPoolMXBean().getTotalConnections()).isLessThanOrEqualTo(2);
        liveUpdates.revalidateSubscriptions();
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void heartbeatDoesNotQueryDatabaseAndClosedClientIsRemoved() throws Exception {
        InputStream stream = openStream("/api/plans/20/events");
        clearInvocations(plans, shares);
        liveUpdates.sendHeartbeats();
        verifyNoInteractions(plans, shares);
        stream.close();
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            liveUpdates.sendHeartbeats();
            assertThat(subscriptionCount()).isZero();
        });
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void expiredPublicShareAndRemovedEditorAreStillDisconnected() throws Exception {
        openStream("/api/public/plans/share/events");
        openStream("/api/plans/20/events");
        share.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(plans.findById(anyLong())).thenReturn(Optional.empty());
        liveUpdates.revalidateSubscriptions();
        assertThat(subscriptionCount()).isZero();
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void waitingPermissionCheckDoesNotBlockConnectionHeartbeat() throws Exception {
        openStream("/api/plans/20/events");
        CountDownLatch checkingPermission = new CountDownLatch(1);
        CountDownLatch releasePermissionCheck = new CountDownLatch(1);
        when(plans.findById(anyLong())).thenAnswer(invocation -> {
            checkingPermission.countDown();
            assertThat(releasePermissionCheck.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(new Plan());
        });
        var authorization = authorizationScheduler.submit(liveUpdates::revalidateSubscriptions);
        try {
            assertThat(checkingPermission.await(2, TimeUnit.SECONDS)).isTrue();
            var heartbeat = heartbeatScheduler.submit(liveUpdates::sendHeartbeats);
            heartbeat.get(1, TimeUnit.SECONDS);
            assertThat(subscriptionCount()).isEqualTo(1);
        } finally {
            releasePermissionCheck.countDown();
            authorization.get(2, TimeUnit.SECONDS);
        }
    }

    private InputStream openStream(String path) throws Exception {
        HttpResponse<InputStream> response = client.send(request(path), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        InputStream body = response.body();
        streams.add(body);
        assertThat(body.read()).isNotEqualTo(-1); // The ready event was flushed.
        return body;
    }

    private String read(String path) throws Exception {
        HttpResponse<String> response = client.send(request(path), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("X-Plan-Share-Token", TOKEN).build();
    }

    private int subscriptionCount() {
        Map<?, ?> subscriptions = (Map<?, ?>) ReflectionTestUtils.getField(liveUpdates, "subscriptions");
        return subscriptions.values().stream().mapToInt(value -> ((Map<?, ?>) value).size()).sum();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class })
    @EntityScan(basePackageClasses = ConnectionProbe.class)
    @Import({ PlanLiveUpdateConfig.class, PlanAccessService.class, PlanLiveUpdateService.class,
            PlanShareService.class, PlanShareController.class, ProbeController.class })
    static class TestApplication { }

    @RestController
    static class ProbeController {
        private final PlanLiveUpdateController controller;
        private final EntityManagerFactory entityManagerFactory;

        ProbeController(PlanLiveUpdateService liveUpdates, EntityManagerFactory entityManagerFactory) {
            this.controller = new PlanLiveUpdateController(liveUpdates);
            this.entityManagerFactory = entityManagerFactory;
        }

        @GetMapping(value = "/api/plans/{planId}/events", produces = "text/event-stream")
        ResponseEntity<SseEmitter> subscribe(@PathVariable Long planId) {
            User user = new User();
            user.setId(1L);
            return controller.subscribe(new CustomUserDetails(user), planId);
        }

        @GetMapping("/probe")
        String ordinaryRequest() {
            return Boolean.toString(TransactionSynchronizationManager.hasResource(entityManagerFactory));
        }
    }
}
