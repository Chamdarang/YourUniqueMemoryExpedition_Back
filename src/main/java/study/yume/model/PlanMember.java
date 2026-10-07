package study.yume.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_member", uniqueConstraints = {
        @UniqueConstraint(name = "uq_plan_member_plan_user", columnNames = {"plan_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PlanMember extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "invited_by_user_id", nullable = false)
    private Long invitedByUserId;

    @Column(name = "share_id")
    private Long shareId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanMemberStatus status = PlanMemberStatus.PENDING;
}
