package study.yume.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import study.yume.model.enums.ScheduleMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlanDay extends BaseTimeEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id",foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Plan plan;


    @Column(nullable = false)
    private String dayName;
    @Column(nullable = false)
    private Integer dayOrder;
    private String memo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleMode scheduleMode = ScheduleMode.DETAILED;

    @OneToMany(mappedBy = "planDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scheduleOrder ASC")
    private List<DaySchedule> schedules = new ArrayList<>();

}
