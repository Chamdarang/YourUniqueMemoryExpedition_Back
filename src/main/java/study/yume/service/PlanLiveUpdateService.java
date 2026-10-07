package study.yume.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import study.yume.repository.PlanShareRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class PlanLiveUpdateService {
    private static final long STREAM_TIMEOUT_MILLIS = 0L;

    private final PlanAccessService planAccessService;
    private final PlanShareRepository planShareRepository;
    private final ConcurrentMap<Long, ConcurrentMap<String, Subscription>> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, Long planId) {
        planAccessService.requireEditor(userId, planId);

        String subscriptionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Subscription subscription = new Subscription(userId, null, emitter);
        register(planId, subscriptionId, subscription, emitter);
        return emitter;
    }

    public SseEmitter subscribePublic(Long planId, String shareTokenHash) {
        requireActiveShare(planId, shareTokenHash);
        String subscriptionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Subscription subscription = new Subscription(null, shareTokenHash, emitter);
        register(planId, subscriptionId, subscription, emitter);
        return emitter;
    }

    private void register(Long planId, String subscriptionId, Subscription subscription, SseEmitter emitter) {
        subscriptions.computeIfAbsent(planId, ignored -> new ConcurrentHashMap<>()).put(subscriptionId, subscription);

        Runnable cleanup = () -> removeSubscription(planId, subscriptionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("planId", planId)));
        } catch (IOException error) {
            cleanup.run();
            emitter.completeWithError(error);
        }
    }

    public void publishAfterCommit(Long planId, Long actorId) {
        if (planId == null) return;
        afterCommit(() -> publish(planId, actorId));
    }

    public void closePlanAfterCommit(Long planId) {
        afterCommit(() -> closePlan(planId));
    }

    public void closeUserAfterCommit(Long planId, Long userId) {
        afterCommit(() -> closeUser(planId, userId));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public void closePlan(Long planId) {
        ConcurrentMap<String, Subscription> planSubscriptions = subscriptions.remove(planId);
        if (planSubscriptions == null) return;
        planSubscriptions.values().forEach(subscription -> subscription.emitter().complete());
    }

    public void closeUser(Long planId, Long userId) {
        ConcurrentMap<String, Subscription> planSubscriptions = subscriptions.get(planId);
        if (planSubscriptions == null) return;
        planSubscriptions.forEach((subscriptionId, subscription) -> {
            if (subscription.userId() != null && subscription.userId().equals(userId)
                    && planSubscriptions.remove(subscriptionId, subscription)) {
                subscription.emitter().complete();
            }
        });
        if (planSubscriptions.isEmpty()) subscriptions.remove(planId, planSubscriptions);
    }

    @Scheduled(fixedRate = 20000)
    public void sendHeartbeats() {
        subscriptions.forEach((planId, planSubscriptions) -> planSubscriptions.forEach((subscriptionId, subscription) -> {
            try {
                authorizeSubscription(planId, subscription);
                subscription.emitter().send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception error) {
                if (planSubscriptions.remove(subscriptionId, subscription)) {
                    subscription.emitter().complete();
                }
            }
        }));
        subscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void publish(Long planId, Long actorId) {
        ConcurrentMap<String, Subscription> planSubscriptions = subscriptions.get(planId);
        if (planSubscriptions == null) return;

        Map<String, Object> payload = Map.of(
                "planId", planId,
                "updatedAt", Instant.now().toString()
        );
        planSubscriptions.forEach((subscriptionId, subscription) -> {
            try {
                authorizeSubscription(planId, subscription);
                subscription.emitter().send(SseEmitter.event().name("plan-updated").data(payload));
            } catch (Exception error) {
                if (planSubscriptions.remove(subscriptionId, subscription)) {
                    subscription.emitter().complete();
                }
            }
        });
        if (planSubscriptions.isEmpty()) subscriptions.remove(planId, planSubscriptions);
    }

    private void removeSubscription(Long planId, String subscriptionId) {
        ConcurrentMap<String, Subscription> planSubscriptions = subscriptions.get(planId);
        if (planSubscriptions == null) return;
        planSubscriptions.remove(subscriptionId);
        if (planSubscriptions.isEmpty()) subscriptions.remove(planId, planSubscriptions);
    }

    private void authorizeSubscription(Long planId, Subscription subscription) {
        if (subscription.userId() != null) {
            planAccessService.requireEditor(subscription.userId(), planId);
            return;
        }
        requireActiveShare(planId, subscription.shareTokenHash());
    }

    private void requireActiveShare(Long planId, String tokenHash) {
        planShareRepository.findByTokenHash(tokenHash)
                .filter(candidate -> candidate.getPlanId().equals(planId))
                .filter(candidate -> candidate.getRevokedAt() == null)
                .filter(candidate -> candidate.getExpiresAt() == null || candidate.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalStateException("공유 링크가 만료되었거나 폐기되었습니다."));
    }

    private record Subscription(Long userId, String shareTokenHash, SseEmitter emitter) {}
}
