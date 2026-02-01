package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Plan extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false,length = 200)
    private String planName;

    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private Integer planDays;

    @Column(length=500)
    private String planMemo;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOrder ASC")
    private List<PlanDay> planDaysList = new ArrayList<>();
}