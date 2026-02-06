package study.yume.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.Map;

@Getter
@Setter
@Entity
public class Spot extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String placeId;

    @Column(nullable = false, length = 200)
    private String spotName;

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

    @Type(JsonType.class)
    @Column(nullable = false,columnDefinition = "json")
    private Map<String, Object> metadata;
}