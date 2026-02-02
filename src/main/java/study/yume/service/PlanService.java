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
import study.yume.model.Plan;
import study.yume.repository.PlanRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanDayService planDayService;

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
        Plan plan = findByUserIdAndId(userId,planId);

        List<PlanDayResponse> schedules = planDayService.getDaysByPlanId(userId, planId);

        return PlanDetailResponse.toDto(plan,schedules);
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
        Plan plan = findByUserIdAndId(userId,planId);

        if(req.planName() !=null) plan.setPlanName(req.planName());
        if(req.planMemo() !=null) plan.setPlanMemo(req.planMemo());

        LocalDate newStart = req.planStartDate() != null ? req.planStartDate() : plan.getPlanStartDate();
        LocalDate newEnd = req.planEndDate() != null ? req.planEndDate() : plan.getPlanEndDate();
        Integer newDays = req.planDays() != null ? req.planDays() : plan.getPlanDays();
        updateAndValidateDates(plan, newStart, newEnd, newDays);

        return PlanResponse.toDto(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> getAllPlans(Long userId, Pageable pageable) {
        return planRepository.findAllByUserId(userId,pageable)
                .map(PlanResponse::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> getFilteredPlans(Long userId, LocalDate from, LocalDate to, List<Integer> months, Pageable pageable) {
        if (from == null && to == null && (months == null || months.isEmpty())) {
            // 필터가 아예 없는 경우 전체 조회
            return getAllPlans(userId,pageable);
        }

        return planRepository.findFilteredPlans(userId, from, to, months,pageable)
                .map(PlanResponse::toDto);
    }

    public void deletePlanById(Long userId, Long planId) {
        Plan plan = findByUserIdAndId(userId,planId);
        planRepository.delete(plan);
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
