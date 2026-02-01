package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.planday.request.PlanDayCreateRequest;
import study.yume.dto.planday.request.PlanDayIndependentCreateRequest;
import study.yume.dto.planday.request.PlanDaySwapRequest;
import study.yume.dto.planday.request.PlanDayUpdateRequest;
import study.yume.dto.planday.response.PlanDayDetailResponse;
import study.yume.dto.planday.response.PlanDayResponse;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.enums.SwapMode;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanDayService {

    private final PlanDayRepository planDayRepository;
    private final PlanRepository planRepository;

    public PlanDayResponse createIndependentDay(Long userId, PlanDayIndependentCreateRequest req){
        PlanDay planDay = new PlanDay();
        planDay.setUserId(userId);
        planDay.setDayName(req.dayName());
        planDay.setDayOrder(1);
        planDay.setPlan(null);

        return PlanDayResponse.toDto(planDayRepository.save(planDay));
    }

    public PlanDayResponse createPlanDay(Long userId,Long planId, PlanDayCreateRequest req){
        Plan plan = findPlanByUserIdAndId(userId,planId);

        if (req.dayOrder() < 1) {
            throw new IllegalArgumentException("일정 순서는 1보다 작을 수 없습니다.");
        }
        if (plan.getPlanDays()==null) {
            throw new IllegalArgumentException("길이가 아직 정해지지 않은 여행에 일정을 추가할 수 없습니다.");
        }
        if (req.dayOrder() > plan.getPlanDays()) {
            throw new IllegalArgumentException(
                    String.format("해당 여행은 %d일 일정입니다. %d일차를 추가할 수 없습니다.", plan.getPlanDays(), req.dayOrder())
            );
        }


        boolean isDuplicate = planDayRepository.existsByUserIdAndPlanIdAndDayOrder(userId, planId, req.dayOrder());
        if (isDuplicate) {
            throw new IllegalArgumentException(req.dayOrder() + "일차 일정은 이미 존재합니다.");
        }

        PlanDay planDay = new PlanDay();
        planDay.setUserId(userId);
        planDay.setDayName(req.dayName());
        planDay.setDayOrder(req.dayOrder());
        planDay.setPlan(plan);

        return PlanDayResponse.toDto(planDayRepository.save(planDay));
    }

    @Transactional(readOnly = true)
    public PlanDayDetailResponse getPlanDayDetail(Long userId, Long dayId) {
        return PlanDayDetailResponse.toDto(findDayByUserIdAndId(userId,dayId));
    }

    @Transactional(readOnly = true)
    public List<PlanDayResponse> getPlanDaysByPlanId(Long userId, Long planId) {
        findPlanByUserIdAndId(userId,planId);

        return planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(userId, planId).stream()
                .map(PlanDayResponse::toDto)
                .toList();
    }


    public PlanDayResponse updatePlanDay(Long userId,Long dayId, PlanDayUpdateRequest req){
        PlanDay planDay = findDayByUserIdAndId(userId,dayId);
        if(req.dayName()!=null) planDay.setDayName(req.dayName());
        return PlanDayResponse.toDto(planDayRepository.save(planDay));
    }

    public PlanDayResponse detachPlanDay(Long userId,Long dayId){
        PlanDay planDay = findDayByUserIdAndId(userId,dayId);
        planDay.setPlan(null);
        planDay.setDayOrder(1);
        return PlanDayResponse.toDto(planDayRepository.save(planDay));
    }

    public void swapPlan(Long userId, PlanDaySwapRequest req){
        PlanDay sourceDay = findDayByUserIdAndId(userId,req.sourceDayId());

        Plan targetPlan = findPlanByUserIdAndId(userId,req.targetPlanId());

        if (req.targetDayOrder() < 1) {
            throw new IllegalArgumentException("일정 순서는 1보다 작을 수 없습니다.");
        }
        if (targetPlan.getPlanDays()==null) {
            throw new IllegalArgumentException("길이가 아직 정해지지 않은 여행에 일정을 추가할 수 없습니다.");
        }
        if (req.targetDayOrder() > targetPlan.getPlanDays()) {
            throw new IllegalArgumentException(
                    String.format("해당 여행은 %d일 일정입니다. %d일차를 추가할 수 없습니다.", targetPlan.getPlanDays(), req.targetDayOrder())
            );
        }
        if (req.swapMode() == SwapMode.SWAP) {
            if (sourceDay.getPlan() == null || !sourceDay.getPlan().getId().equals(req.targetPlanId())) {
                throw new IllegalArgumentException("자리바꿈은 동일한 여행 계획 내에서만 가능합니다.");
            }
        }

        List<PlanDay> existPlanDays = planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(userId,req.targetPlanId());
        PlanDay conflictPlanDay = existPlanDays.stream()
                .filter(d->d.getDayOrder().equals(req.targetDayOrder()))
                .findFirst()
                .orElse(null);

        if(conflictPlanDay!=null&& !conflictPlanDay.getId().equals(sourceDay.getId())) {
            handleConflict(conflictPlanDay,req.swapMode(),existPlanDays,req.targetDayOrder(),targetPlan.getPlanDays(),sourceDay.getDayOrder());
        }

        sourceDay.setPlan(targetPlan);
        sourceDay.setDayOrder(req.targetDayOrder());
        planDayRepository.save(sourceDay);
    }

    public void deletePlanDay(Long userId,Long dayId){
        PlanDay planDay = findDayByUserIdAndId(userId,dayId);
        planDayRepository.delete(planDay);
    }

    @Transactional(readOnly = true)
    public List<PlanDayResponse> getDaysByPlanId(Long userId, Long planId){
        return planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(userId, planId).stream()
                .map(PlanDayResponse::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanDayResponse> getAllIndependentDay(Long userId){
        return planDayRepository.findAllByUserIdAndPlanIsNull(userId).stream()
                .map(PlanDayResponse::toDto)
                .toList();
    }

    /**
     * 일정 변경 시 발생하는 충돌(Conflict)을 설정된 모드에 따라 처리.
     * @param conflictPlanDay  이동하려는 위치(targetDayOrder)에 이미 존재하는 기존 일정
     * @param mode             충돌 처리 모드 (REPLACE, INDEPENDENT, SWAP, SHIFT)
     * @param existPlanDays    타겟 계획(Plan)에 포함된 현재 모든 일정 리스트 (SHIFT 연산 시 참조)
     * @param targetDayOrder   이동하고자 하는 목표 순서 (n일차)
     * @param targetPlanDays   타겟 계획(Plan)의 총 여행 기간 (SHIFT 시 범위 초과 체크용)
     * @param sourceDayOrder   이동하는 일정이 원래 위치했던 순서 (SWAP 시 목표 일정을 이동시킬 위치)
     */
    private void handleConflict(PlanDay conflictPlanDay, SwapMode mode, List<PlanDay> existPlanDays, Integer targetDayOrder, Integer targetPlanDays, Integer sourceDayOrder) {
        switch(mode){
            case REPLACE: // 기존의 것을 지우고 늘어감
                planDayRepository.delete(conflictPlanDay);
                break;

            case SWAP: // 기존의 것과 자리를 바꿈
                conflictPlanDay.setDayOrder(sourceDayOrder);
                planDayRepository.save(conflictPlanDay);
                break;

            case INDEPENDENT: // 기존의 것을 독립시키고 들어감
                conflictPlanDay.setPlan(null);
                conflictPlanDay.setDayOrder(1);
                planDayRepository.save(conflictPlanDay);
                break;

            case SHIFT: // 특정 위치에 들어가며 하루씩 밀어냄
                List<Integer> occupiedOrders = existPlanDays.stream()
                        .map(PlanDay::getDayOrder)
                        .toList();


                boolean hasEmptySlot = false;
                for (int i = targetDayOrder; i <= targetPlanDays; i++) {
                    if (!occupiedOrders.contains(i)) {
                        hasEmptySlot = true;
                        break;
                    }
                }

                // 3. 빈 공간이 없다면(꽉 찼다면) 에러 발생
                if (!hasEmptySlot) {
                    throw new IllegalArgumentException(
                            String.format("해당 구간 이후로 일정을 밀어낼 빈 공간이 없습니다. (최대 %d일)", targetPlanDays)
                    );
                }

                existPlanDays.stream()
                        .filter(d->d.getDayOrder()>=targetDayOrder)
                        .forEach(d->{
                            d.setDayOrder(d.getDayOrder()+1);
                            planDayRepository.save(d);
                        });
                break;
        }
    }

    private PlanDay findDayByUserIdAndId(Long userId, Long dayId) {
        return planDayRepository.findByUserIdAndId(userId,dayId)
                .orElseThrow(()->new EntityNotFoundException("일정을 찾을 수 없습니다."));
    }

    private Plan findPlanByUserIdAndId(Long userId, Long planId) {
        return planRepository.findByUserIdAndId(userId, planId)
                .orElseThrow(() -> new EntityNotFoundException("여행 계획을 찾을 수 없습니다."));
    }
}
