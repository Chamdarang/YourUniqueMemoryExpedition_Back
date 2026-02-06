package study.yume.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import study.yume.model.enums.SpotType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
public class SpotUser extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id",foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Spot spot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 40)
    private SpotType spotType;

    @Column(length = 200)
    private String customName;
    @Column(nullable = false)
    private Boolean isVisit = false;
    @Column(columnDefinition = "TEXT")
    private String description;

    @Type(JsonType.class)
    @Column(nullable = false,columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();

    @OneToMany(mappedBy = "spotUser", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpotPurchase> spotPurchases = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "spot_group_map",
            joinColumns = @JoinColumn(name = "spot_user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            inverseJoinColumns = @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    )
    private List<SpotGroup> spotGroup = new ArrayList<>();

    @OneToMany(mappedBy = "spotUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("visitedAt DESC")
    private List<SpotVisitHistory> spotVisitHistory = new ArrayList<>();
}
