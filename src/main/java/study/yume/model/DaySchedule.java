package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import study.yume.model.enums.SpotType;
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
    @JoinColumn(name = "spot_user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private SpotUser spotUser;

    @Column(length = 200)
    private String spotNameSnapshot;
    @Column(columnDefinition = "POINT SRID 4326")
    private Point spotLocationSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private SpotType spotTypeSnapshot;
    @Column(nullable = false)
    private Boolean isChecked=false;

    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private boolean fixedStartTime;
    @Column(nullable = false)
    private int duration; //단위: 분
    @Column(nullable = false)
    private LocalTime endTime;

    private int movingDuration;

    private int extraDuration; // 체류 인저리타임
    private int extraMovingDuration; // 이동 인저리타임

    @Enumerated(EnumType.STRING)
    private Transportation transportation;
    private String memo;
    private String movingMemo;
}
