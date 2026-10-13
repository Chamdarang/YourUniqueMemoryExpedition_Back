package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import study.yume.model.enums.PlanReservationStatus;
import study.yume.model.enums.PlanReservationType;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
public class PlanReservation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanReservationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanReservationStatus status = PlanReservationStatus.RESERVED;

    @Column(length = 200)
    private String provider;

    @Column(length = 120)
    private String confirmationCode;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;

    @Column(length = 1000)
    private String bookingUrl;

    @Column(length = 500)
    private String address;

    @Column(length = 2000)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_day_id")
    private PlanDay planDay;
}
