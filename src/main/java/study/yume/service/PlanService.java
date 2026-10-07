package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.plan.request.PlanCreateRequest;
import study.yume.dto.plan.request.PlanUpdateRequest;
import study.yume.dto.plan.response.PlanDetailResponse;
import study.yume.dto.plan.response.PlanResponse;
import study.yume.dto.planday.response.PlanDayResponse;
import study.yume.exception.PlanEntityEditConflictException;
import study.yume.model.Plan;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanDayRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final PlanDayService planDayService;
    private final PlanAccessService planAccessService;
    private final PlanLiveUpdateService planLiveUpdateService;

    public PlanResponse createPlan(Long userId, PlanCreateRequest req) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setPlanName(req.planName());
        plan.setPlanMemo(req.planMemo());

        updateAndValidateDates(plan,req.planStartDate(),req.planEndDate(),req.planDays());


        return PlanResponse.toDto(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanDetailResponse getPlanById(Long userId,Long planId) {
        Plan plan = planAccessService.requireEditor(userId, planId);

        List<PlanDayResponse> schedules = planDayService.getDaysByPlanId(userId, planId);

        String accessRole = plan.getUserId().equals(userId) ? "OWNER" : "EDITOR";
        return PlanDetailResponse.toDto(plan, accessRole, schedules);
    }


    @Transactional(readOnly = true)
    public PlanResponse getUpcomingPlan(Long userId) {
        List<Plan> plans = planRepository.findUpcomingOrCurrentPlan(
                userId,
                LocalDate.now(),
                PageRequest.of(0, 1)
        );

        return plans.stream()
                .findFirst()
                .map(PlanResponse::toDto)
                .orElse(null); // 데이터가 없으면 null을 반환하여 ApiResponse 규격 유지
    }

    public PlanResponse updatePlan(Long userId, Long planId, PlanUpdateRequest req) {
        Plan plan = planAccessService.requireEditor(userId, planId);

        if (req.expectedVersion() == null) {
            throw new IllegalArgumentException("계획 버전 정보가 없습니다. 최신 내용을 다시 불러와 주세요.");
        }
        if (!req.expectedVersion().equals(plan.getVersion())) {
            throw new PlanEntityEditConflictException("여행 계획이 다른 사용자에 의해 변경되었습니다.", PlanResponse.toDto(plan));
        }

        if(req.planName() !=null) plan.setPlanName(req.planName());
        if(req.planMemo() !=null) plan.setPlanMemo(req.planMemo());

        LocalDate newStart = req.planStartDate() != null ? req.planStartDate() : plan.getPlanStartDate();
        LocalDate newEnd = req.planEndDate() != null ? req.planEndDate() : plan.getPlanEndDate();
        Integer newDays = req.planDays() != null ? req.planDays() : plan.getPlanDays();
        List<study.yume.model.PlanDay> assignedDays = newDays == null ? List.of()
                : planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(plan.getUserId(), planId);
        List<study.yume.model.PlanDay> daysOutsideNewPeriod = newDays == null ? List.of()
                : assignedDays.stream().filter(day -> day.getDayOrder() > newDays).toList();
        if (!plan.getUserId().equals(userId) && !daysOutsideNewPeriod.isEmpty()) {
            throw new IllegalArgumentException("일정이 등록된 범위보다 짧게 줄이려면 여행 소유자가 날짜를 먼저 정리해야 합니다.");
        }
        List<Long> detachDayIds = req.detachDayIds() == null ? List.of() : req.detachDayIds();
        if (plan.getUserId().equals(userId)
                && daysOutsideNewPeriod.stream().anyMatch(day -> !detachDayIds.contains(day.getId()))) {
            throw new IllegalArgumentException("여행 기간 밖의 일차를 먼저 하루 코스 보관함으로 옮겨 주세요.");
        }
        if (!detachDayIds.isEmpty()) {
            if (newDays == null) {
                throw new IllegalArgumentException("날짜를 분리하려면 여행 기간을 먼저 지정해야 합니다.");
            }
            if (!plan.getUserId().equals(userId)) {
                throw new IllegalArgumentException("날짜 분리는 여행 소유자만 할 수 있습니다.");
            }
            List<study.yume.model.PlanDay> selectedDays = assignedDays.stream()
                    .filter(day -> detachDayIds.contains(day.getId()))
                    .toList();
            if (selectedDays.size() != detachDayIds.stream().distinct().count()
                    || selectedDays.stream().anyMatch(day -> day.getDayOrder() <= newDays)) {
                throw new IllegalArgumentException("여행 기간 범위를 벗어난 날짜만 분리할 수 있습니다.");
            }
            String planName = plan.getPlanName();
            selectedDays.forEach(day -> {
                String memo = day.getMemo() == null ? "" : day.getMemo();
                day.setMemo(memo + (memo.isEmpty() ? "" : "\n") + "[" + planName + "에서 분리됨]");
                day.setPlan(null);
                day.setDayOrder(1);
            });
            planDayRepository.saveAll(selectedDays);
        }
        updateAndValidateDates(plan, newStart, newEnd, newDays);

        Plan saved = planRepository.save(plan);
        planLiveUpdateService.publishAfterCommit(planId, userId);
        return PlanResponse.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> getAllPlans(Long userId, Pageable pageable) {
        return planRepository.findAllByUserId(userId,pageable)
                .map(PlanResponse::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> getFilteredPlans(Long userId, LocalDate from, LocalDate to, List<Integer> months, String status, Pageable pageable) {
        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase();
        if (!List.of("ALL", "UPCOMING", "PAST").contains(normalizedStatus)) {
            throw new IllegalArgumentException("올바르지 않은 여행 상태 필터입니다.");
        }

        if (from == null && to == null && (months == null || months.isEmpty()) && normalizedStatus.equals("ALL")) {
            // 필터가 아예 없는 경우 전체 조회
            return getAllPlans(userId,pageable);
        }

        return planRepository.findFilteredPlans(userId, from, to, months, normalizedStatus, LocalDate.now(), pageable)
                .map(PlanResponse::toDto);
    }

    public void deletePlanById(Long userId, Long planId) {
        Plan plan = findByUserIdAndId(userId,planId);
        planRepository.delete(plan);
        planLiveUpdateService.closePlanAfterCommit(planId);
    }

    private Plan findByUserIdAndId(Long userId, Long planId) {
        return planRepository.findByUserIdAndId(userId,planId)
                .orElseThrow(()->new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
    }

    private void updateAndValidateDates(Plan plan, LocalDate start, LocalDate end, Integer days) {

        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("시작일은 종료일보다 빨라야 합니다.");
        }


        if (start != null && end != null && days != null) {
            // [Case 1] 세 개 다 있음 -> 검증
            long diff = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (diff != days) {
                throw new IllegalArgumentException(
                        String.format("입력된 기간(%d일)과 날짜 차이(%d일)가 일치하지 않습니다.", days, diff)
                );
            }
        } else if (start != null && end != null) {
            // [Case 2] 시작, 종료 있음 -> 기간 계산
            days = (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        } else if (start != null && days != null) {
            // [Case 3] 시작, 기간 있음 -> 종료 계산
            end = start.plusDays(days - 1);
        } else if (end != null && days != null) {
            // [Case 4] 종료, 기간 있음 -> 시작 계산
            start = end.minusDays(days - 1);
        }

        // 엔티티에 최종 확정된 값 반영
        plan.setPlanStartDate(start);
        plan.setPlanEndDate(end);
        plan.setPlanDays(days);
    }
}
