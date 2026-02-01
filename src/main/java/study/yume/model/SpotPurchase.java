package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class SpotPurchase extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id",nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Spot spot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 30)
    private PurchaseKind kind;

    @Column(length = 40)
    private String category;

    @Column(length = 200)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.UNKNOWN; // 구매여부

    private int quantity;

    private BigDecimal price;

    @Column(length = 3)
    private String currency;

    private LocalDate acquiredDate;

    @Column(length = 500)
    private String note;

}
