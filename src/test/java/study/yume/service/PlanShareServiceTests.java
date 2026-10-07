package study.yume.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.PlanMember;
import study.yume.model.PlanMemberStatus;
import study.yume.model.PlanShare;
import study.yume.model.enums.ScheduleMode;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanMemberRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.PlanShareRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanShareServiceTests {
    private static final String TOKEN = "A".repeat(43);

    @Mock private PlanRepository planRepository;
    @Mock private PlanShareRepository planShareRepository;
    @Mock private PlanMemberRepository planMemberRepository;
    @Mock private PlanDayRepository planDayRepository;
    @Mock private DayScheduleRepository dayScheduleRepository;
    @Mock private PlanShareTokenCipher planShareTokenCipher;
    @Mock private PlanLiveUpdateService planLiveUpdateService;

    @Test
    void associatesAcceptedPendingInvitationWithTheEditLink() {
        PlanShare share = activeEditShare();
        Plan plan = plan();
        PlanMember pending = new PlanMember();
        pending.setPlanId(plan.getId());
        pending.setUserId(2L);
        pending.setStatus(PlanMemberStatus.PENDING);
        when(planShareRepository.findByTokenHash(anyString())).thenReturn(Optional.of(share));
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(planMemberRepository.findByPlanIdAndUserId(plan.getId(), 2L)).thenReturn(Optional.of(pending));

        service().acceptEditLink(2L, TOKEN);

        assertThat(pending.getStatus()).isEqualTo(PlanMemberStatus.ACCEPTED);
        assertThat(pending.getShareId()).isEqualTo(share.getId());
        verify(planMemberRepository).save(pending);
    }

    @Test
    void publicPlanMetadataDoesNotLoadAnyDaySchedulesUntilRequested() {
        PlanShare share = activeEditShare();
        Plan plan = plan();
        PlanDay day = new PlanDay();
        day.setId(41L);
        day.setUserId(plan.getUserId());
        day.setDayName("Kyoto");
        day.setDayOrder(1);
        day.setScheduleMode(ScheduleMode.DETAILED);
        when(planShareRepository.findByTokenHash(anyString())).thenReturn(Optional.of(share));
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(planDayRepository.findAllByUserIdAndPlanIdOrderByDayOrderAsc(plan.getUserId(), plan.getId())).thenReturn(List.of(day));

        var result = service().getPublicPlan(TOKEN).orElseThrow();

        assertThat(result.days()).hasSize(1);
        assertThat(result.days().get(0).dayOrder()).isEqualTo(1);
        verifyNoInteractions(dayScheduleRepository);
    }

    private PlanShareService service() {
        return new PlanShareService(planRepository, planShareRepository, planMemberRepository, planDayRepository,
                dayScheduleRepository, planShareTokenCipher, planLiveUpdateService);
    }

    private PlanShare activeEditShare() {
        PlanShare share = new PlanShare();
        share.setId(5L);
        share.setPlanId(20L);
        share.setAccessMode("EDIT");
        return share;
    }

    private Plan plan() {
        Plan plan = new Plan();
        plan.setId(20L);
        plan.setUserId(1L);
        plan.setPlanName("Trip");
        return plan;
    }
}
