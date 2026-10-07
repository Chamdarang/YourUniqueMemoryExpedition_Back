package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uq_visit_history_user_schedule",
        columnNames = {"user_id", "schedule_id"}
))
public class SpotVisitHistory{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    // Keep the visit snapshot even if the originating schedule is deleted.
    @Column(name = "schedule_id")
    private Long scheduleId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SpotUser spotUser;
    @Column(nullable = false)
    private Long dayId;
    private Long planId;
    @Column(nullable = false, length = 200)
    private String dayNameSnapshot;
    @Column(length = 200)
    private String planNameSnapshot;
    @Column(nullable = false)
    private LocalDate visitedAt;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
