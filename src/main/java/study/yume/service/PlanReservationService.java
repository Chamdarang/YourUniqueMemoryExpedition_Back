package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.planreservation.request.PlanReservationRequest;
import study.yume.dto.planreservation.response.PlanReservationResponse;
import study.yume.model.Plan;
import study.yume.model.PlanReservation;
import study.yume.model.PlanDay;
import study.yume.model.enums.PlanReservationStatus;
import study.yume.repository.PlanReservationRepository;
import study.yume.repository.PlanDayRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanReservationService {
    private final PlanAccessService planAccessService;
    private final PlanReservationRepository reservationRepository;
    private final PlanDayRepository planDayRepository;
    private final PlanLiveUpdateService planLiveUpdateService;

    @Transactional(readOnly = true)
    public List<PlanReservationResponse> getReservations(Long userId, Long planId) {
        planAccessService.requireEditor(userId, planId);
        return reservationRepository.findAllByPlanIdOrderByStartDateAscStartTimeAscIdAsc(planId)
                .stream().map(PlanReservationResponse::from).toList();
    }

    public PlanReservationResponse create(Long userId, Long planId, PlanReservationRequest request) {
        Plan plan = planAccessService.requireEditor(userId, planId);
        validate(request, plan);
        PlanReservation reservation = new PlanReservation();
        reservation.setPlan(plan);
        apply(reservation, request);
        PlanReservationResponse response = PlanReservationResponse.from(reservationRepository.save(reservation));
        planLiveUpdateService.publishAfterCommit(planId, userId);
        return response;
    }

    public PlanReservationResponse update(Long userId, Long planId, Long reservationId, PlanReservationRequest request) {
        Plan plan = planAccessService.requireEditor(userId, planId);
        PlanReservation reservation = reservationRepository.findByIdAndPlanId(reservationId, planId)
                .orElseThrow(() -> new EntityNotFoundException("예약 정보를 찾을 수 없습니다."));
        validate(request, plan);
        apply(reservation, request);
        PlanReservationResponse response = PlanReservationResponse.from(reservationRepository.save(reservation));
        planLiveUpdateService.publishAfterCommit(planId, userId);
        return response;
    }

    public void delete(Long userId, Long planId, Long reservationId) {
        planAccessService.requireEditor(userId, planId);
        PlanReservation reservation = reservationRepository.findByIdAndPlanId(reservationId, planId)
                .orElseThrow(() -> new EntityNotFoundException("예약 정보를 찾을 수 없습니다."));
        reservationRepository.delete(reservation);
        planLiveUpdateService.publishAfterCommit(planId, userId);
    }

    private void validate(PlanReservationRequest request, Plan plan) {
        if (request == null || request.type() == null) throw new IllegalArgumentException("예약 종류를 선택해 주세요.");
        requireText(request.title(), 200, "예약 이름");
        if (request.status() == null) throw new IllegalArgumentException("예약 상태를 선택해 주세요.");
        requireMaxLength(request.provider(), 200, "업체명");
        requireMaxLength(request.confirmationCode(), 120, "예약번호");
        requireMaxLength(request.bookingUrl(), 1000, "예약 링크");
        requireMaxLength(request.address(), 500, "주소");
        requireMaxLength(request.memo(), 2000, "메모");
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("이용 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (request.dayId() != null && planDayRepository.findByIdAndPlanId(request.dayId(), plan.getId()).isEmpty())
            throw new IllegalArgumentException("연결할 일차가 이 여행에 포함되지 않습니다.");
        validateUrl(request.bookingUrl());
    }

    private void validateUrl(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = new URI(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("예약 링크는 http 또는 https 주소여야 합니다.");
            }
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("예약 링크 형식이 올바르지 않습니다.");
        }
    }

    private void apply(PlanReservation reservation, PlanReservationRequest request) {
        reservation.setType(request.type());
        reservation.setTitle(request.title().trim());
        reservation.setStatus(request.status() == null ? PlanReservationStatus.RESERVED : request.status());
        reservation.setProvider(trimToNull(request.provider()));
        reservation.setConfirmationCode(trimToNull(request.confirmationCode()));
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setBookingUrl(trimToNull(request.bookingUrl()));
        reservation.setAddress(trimToNull(request.address()));
        reservation.setMemo(trimToNull(request.memo()));
        reservation.setPlanDay(request.dayId() == null ? null : planDayRepository.findByIdAndPlanId(request.dayId(), reservation.getPlan().getId())
                .orElseThrow(() -> new IllegalArgumentException("연결할 일차가 이 여행에 포함되지 않습니다.")));
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void requireText(String value, int limit, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        requireMaxLength(value, limit, label);
    }

    private void requireMaxLength(String value, int limit, String label) {
        if (value != null && value.length() > limit) throw new IllegalArgumentException(label + "은(는) " + limit + "자 이하여야 합니다.");
    }
}
