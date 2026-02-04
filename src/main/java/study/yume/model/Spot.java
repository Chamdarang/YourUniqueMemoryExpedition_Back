package study.yume.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import study.yume.model.enums.SpotType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
public class Spot extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private String placeId;

    @Column(nullable = false, length = 200)
    private String spotName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 40)
    private SpotType spotType;

    @Column(nullable = false)
    private String address;
    private String shortAddress;
    @Column(length = 500)
    private String website;
    @Column(length = 500)
    private String googleMapUrl;
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "POINT SRID 4326",nullable = false)
    private Point location;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(nullable = false)
    private Boolean isVisit = false;
    private LocalDate visitDate;

    @Type(JsonType.class)
    @Column(nullable = false,columnDefinition = "json")
    private Map<String, Object> metadata;

    //todo: 공통(이름,주소,좌표,구글지도 등)을 제외한 데이터 user_spot 테이블 만들어 분리

    @OneToMany(mappedBy = "spot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpotPurchase> spotPurchases = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "spot_group_map",
            joinColumns = @JoinColumn(name = "spot_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
            inverseJoinColumns = @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    )
    private List<SpotGroup> spotGroup = new ArrayList<>();
}