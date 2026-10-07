package study.yume.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "schedule_visit_status", uniqueConstraints = {
        @UniqueConstraint(name = "uq_schedule_visit_user", columnNames = {"schedule_id", "user_id"})
})
public class ScheduleVisitStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spot_id")
    private Long spotId;

    @Column(name = "spot_link_cleared", nullable = false)
    private boolean spotLinkCleared;

    @Column(name = "is_checked", nullable = false)
    private boolean isChecked;

    @Column(name = "is_skipped", nullable = false)
    private boolean isSkipped;
}
