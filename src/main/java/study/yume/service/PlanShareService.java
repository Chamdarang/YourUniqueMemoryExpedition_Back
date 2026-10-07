package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import study.yume.dto.planshare.response.PlanShareLinkResponse;
import study.yume.dto.planshare.response.PlanShareStatusResponse;
import study.yume.dto.planshare.response.PublicPlanShareResponse;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.PlanShare;
import study.yume.model.PlanMember;
import study.yume.model.PlanMemberStatus;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanShareRepository;
import study.yume.repository.PlanMemberRepository;

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
    private final PlanMemberRepository planMemberRepository;
    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final PlanShareTokenCipher planShareTokenCipher;
    private final PlanLiveUpdateService planLiveUpdateService;

    public PlanShareLinkResponse createOrRotate(Long userId, Long planId, Integer expiresInDays, String accessMode) {
        if (expiresInDays != null && !ALLOWED_EXPIRY_DAYS.contains(expiresInDays)) {
            throw new IllegalArgumentException("공유 링크 만료 기간은 1일, 7일, 30일 또는 만료 없음만 선택할 수 있습니다.");
        }
        if (!"VIEW".equals(accessMode) && !"EDIT".equals(accessMode)) {
            throw new IllegalArgumentException("공유 권한은 보기 전용 또는 편집 가능 중에서 선택해 주세요.");
        }
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = expiresInDays == null ? null : issuedAt.plusDays(expiresInDays);
        String token = createToken();
        PlanShare share = planShareRepository.findByPlanId(planId).orElseGet(PlanShare::new);
        boolean rotatingExistingShare = share.getId() != null;
        if (rotatingExistingShare) removeShareMembers(share);
        share.setPlanId(planId);
        share.setTokenHash(hashToken(token));
        share.setTokenCiphertext(planShareTokenCipher.encrypt(token, planId));
        share.setRevokedAt(null);
        share.setExpiresAt(expiresAt);
        share.setAccessMode(accessMode);
        planShareRepository.saveAndFlush(share);
        if (rotatingExistingShare) planLiveUpdateService.closePlanAfterCommit(planId);
        return new PlanShareLinkResponse(token, issuedAt, expiresAt, accessMode);
    }

    @Transactional(readOnly = true)
    public PlanShareStatusResponse getStatus(Long userId, Long planId) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        return planShareRepository.findByPlanId(planId)
                .map(share -> {
                    LocalDateTime now = LocalDateTime.now();
                    boolean active = isActive(share, now);
                    String state = active ? "ACTIVE"
                            : share.getRevokedAt() != null ? "REVOKED" : "EXPIRED";
                    String token = active && share.getTokenCiphertext() != null
                            ? planShareTokenCipher.decrypt(share.getTokenCiphertext(), planId)
                            : null;
                    return new PlanShareStatusResponse(state, active, share.getUpdatedAt(), share.getExpiresAt(), token, share.getAccessMode());
                })
                .orElseGet(() -> new PlanShareStatusResponse("NOT_CREATED", false, null, null, null, "VIEW"));
    }

    public void revoke(Long userId, Long planId) {
        planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        planShareRepository.findByPlanId(planId).ifPresent(share -> {
            if (share.getRevokedAt() == null) {
                removeShareMembers(share);
                share.setRevokedAt(LocalDateTime.now());
                share.setTokenCiphertext(null);
                planShareRepository.save(share);
            }
        });
        planLiveUpdateService.closePlanAfterCommit(planId);
    }

    public Long acceptEditLink(Long userId, String token) {
        PlanShare share = findActiveShare(token)
                .filter(value -> "EDIT".equals(value.getAccessMode()))
                .orElseThrow(() -> new EntityNotFoundException("편집 링크가 만료되었거나 더 이상 사용할 수 없습니다."));
        Plan plan = planRepository.findById(share.getPlanId())
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
        if (plan.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 소유의 계획에는 참여할 수 없습니다.");
        }
        PlanMember existing = planMemberRepository.findByPlanIdAndUserId(plan.getId(), userId).orElse(null);
        if (existing == null) {
            PlanMember member = new PlanMember();
            member.setPlanId(plan.getId());
            member.setUserId(userId);
            member.setInvitedByUserId(plan.getUserId());
            member.setShareId(share.getId());
            member.setStatus(PlanMemberStatus.ACCEPTED);
            planMemberRepository.save(member);
        } else if (existing.getStatus() == PlanMemberStatus.PENDING) {
            existing.setShareId(share.getId());
            existing.setStatus(PlanMemberStatus.ACCEPTED);
            planMemberRepository.save(existing);
        }
        planLiveUpdateService.publishAfterCommit(plan.getId(), userId);
        return plan.getId();
    }

    @Transactional(readOnly = true)
    public Optional<PublicPlanShareResponse> getPublicPlan(String token) {
        return findActiveShare(token)
                .flatMap(share -> planRepository.findById(share.getPlanId())
                        .map(plan -> toPublicResponse(plan, share)));
    }

    @Transactional(readOnly = true)
    public Optional<List<PublicPlanShareResponse.Schedule>> getPublicPlanDaySchedules(String token, Integer dayOrder) {
        if (dayOrder == null || dayOrder < 1) return Optional.empty();
        return findActiveShare(token)
                .flatMap(share -> planRepository.findById(share.getPlanId())
                        .flatMap(plan -> planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(plan.getUserId(), share.getPlanId())
                                .stream()
                                .filter(day -> day.getDayOrder().equals(dayOrder))
                                .findFirst()
                                .map(day -> dayScheduleRepository
                                        .findAllByUserIdAndPlanDayIdOrderByScheduleOrderAsc(plan.getUserId(), day.getId())
                                        .stream()
                                        .map(this::toPublicSchedule)
                                        .toList())));
    }

    public SseEmitter subscribePublicPlan(String token) {
        PlanShare share = findActiveShare(token)
                .orElseThrow(() -> new EntityNotFoundException("공유 링크가 만료되었거나 더 이상 사용할 수 없습니다."));
        return planLiveUpdateService.subscribePublic(share.getPlanId(), share.getTokenHash());
    }

    private Optional<PlanShare> findActiveShare(String token) {
        if (token == null || !token.matches(TOKEN_PATTERN)) return Optional.empty();
        return planShareRepository.findByTokenHash(hashToken(token)).filter(this::isActive);
    }

    private void removeShareMembers(PlanShare share) {
        planMemberRepository.deleteAll(planMemberRepository.findAllByShareId(share.getId()));
    }

    private boolean isActive(PlanShare share) {
        return isActive(share, LocalDateTime.now());
    }

    private boolean isActive(PlanShare share, LocalDateTime now) {
        return share.getRevokedAt() == null
                && (share.getExpiresAt() == null || share.getExpiresAt().isAfter(now));
    }

    private PublicPlanShareResponse toPublicResponse(Plan plan, PlanShare share) {
        Long planId = share.getPlanId();
        List<PublicPlanShareResponse.Day> days = planDayRepository
                .findAllByUserIdAndPlanIdOrderByDayOrderAsc(plan.getUserId(), planId)
                .stream()
                .map(day -> new PublicPlanShareResponse.Day(
                        day.getDayName(),
                        day.getDayOrder(),
                        day.getMemo(),
                        day.getScheduleMode()
                ))
                .toList();

        return new PublicPlanShareResponse(
                planId,
                share.getAccessMode(),
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
