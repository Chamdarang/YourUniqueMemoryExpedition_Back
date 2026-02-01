package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import study.yume.model.enums.Transportation;

import java.time.LocalTime;

@Entity
@Getter
@Setter
public class DaySchedule extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_day_id", nullable = false,foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private PlanDay planDay;

    @Column(nullable = false)
    private int scheduleOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Spot spot;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private int duration; //단위: 분

    @Column(nullable = false)
    private LocalTime endTime;



    private int movingDuration;

    @Enumerated(EnumType.STRING)
    private Transportation transportation;
    private String memo;
    private String movingMemo;
}
