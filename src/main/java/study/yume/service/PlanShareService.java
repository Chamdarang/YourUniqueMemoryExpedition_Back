package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.planshare.response.PlanShareLinkResponse;
import study.yume.dto.planshare.response.PlanShareStatusResponse;
import study.yume.dto.planshare.response.PublicPlanShareResponse;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.PlanShare;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanShareRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanShareService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{43}";
    private static final Set<Integer> ALLOWED_EXPIRY_DAYS = Set.of(1, 7, 30);

    private final PlanRepository planRepository;
    private final PlanShareRepository planShareRepository;
    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final PlanShareTokenCipher planShareTokenCipher;

    public PlanShareLinkResponse createOrRotate(Long userId, Long planId, Integer expiresInDays) {
        if (expiresInDays != null && !ALLOWED_EXPIRY_DAYS.contains(expiresInDays)) {
            throw new IllegalArgumentException("공유 링크 만료 기간은 1일, 7일, 30일 또는 만료 없음만 선택할 수 있습니다.");
        }
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = expiresInDays == null ? null : issuedAt.plusDays(expiresInDays);
        String token = createToken();
        PlanShare share = planShareRepository.findByPlanId(planId).orElseGet(PlanShare::new);
        share.setPlanId(planId);
        share.setTokenHash(hashToken(token));
        share.setTokenCiphertext(planShareTokenCipher.encrypt(token, planId));
        share.setRevokedAt(null);
        share.setExpiresAt(expiresAt);
        planShareRepository.saveAndFlush(share);
        return new PlanShareLinkResponse(token, issuedAt, expiresAt);
    }

    @Transactional(readOnly = true)
    public PlanShareStatusResponse getStatus(Long userId, Long planId) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        return planShareRepository.findByPlanId(planId)
                .map(share -> {
                    boolean active = isActive(share);
                    String token = active && share.getTokenCiphertext() != null
                            ? planShareTokenCipher.decrypt(share.getTokenCiphertext(), planId)
                            : null;
                    return new PlanShareStatusResponse(active, share.getUpdatedAt(), share.getExpiresAt(), token);
                })
                .orElseGet(() -> new PlanShareStatusResponse(false, null, null, null));
    }

    public void revoke(Long userId, Long planId) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        planShareRepository.findByPlanId(planId).ifPresent(share -> {
            if (share.getRevokedAt() == null) {
                share.setRevokedAt(LocalDateTime.now());
                share.setTokenCiphertext(null);
                planShareRepository.save(share);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<PublicPlanShareResponse> getPublicPlan(String token) {
        if (token == null || !token.matches(TOKEN_PATTERN)) return Optional.empty();

        return planShareRepository.findByTokenHash(hashToken(token))
                .filter(this::isActive)
                .flatMap(share -> planRepository.findById(share.getPlanId())
                        .map(plan -> toPublicResponse(plan, share.getPlanId())));
    }

    private boolean isActive(PlanShare share) {
        return share.getRevokedAt() == null
                && (share.getExpiresAt() == null || share.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private PublicPlanShareResponse toPublicResponse(Plan plan, Long planId) {
        List<PublicPlanShareResponse.Day> days = planDayRepository
                .findAllByUserIdAndPlanIdOrderByDayOrderAsc(plan.getUserId(), planId)
                .stream()
                .map(day -> new PublicPlanShareResponse.Day(
                        day.getDayName(),
                        day.getDayOrder(),
                        day.getMemo(),
                        day.getScheduleMode(),
                        dayScheduleRepository
                                .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(plan.getUserId(), day.getId())
                                .stream()
                                .map(this::toPublicSchedule)
                                .toList()
                ))
                .toList();

        return new PublicPlanShareResponse(
                plan.getPlanName(),
                plan.getPlanStartDate(),
                plan.getPlanEndDate(),
                plan.getPlanMemo(),
                days
        );
    }

    private PublicPlanShareResponse.Schedule toPublicSchedule(DaySchedule schedule) {
        return new PublicPlanShareResponse.Schedule(
                schedule.getScheduleOrder(),
                schedule.getSpotNameSnapshot(),
                schedule.getSpotTypeSnapshot(),
                schedule.getSpotLocationSnapshot() == null ? null : schedule.getSpotLocationSnapshot().getY(),
                schedule.getSpotLocationSnapshot() == null ? null : schedule.getSpotLocationSnapshot().getX(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getDuration() + schedule.getExtraDuration(),
                schedule.getMovingDuration() + schedule.getExtraMovingDuration(),
                schedule.getTransportation(),
                schedule.getMemo(),
                schedule.getMovingMemo()
        );
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
