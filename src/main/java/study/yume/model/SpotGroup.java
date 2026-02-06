package study.yume.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class SpotGroup extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String groupName;
    @ManyToMany(mappedBy = "spotGroup")
    private List<SpotUser> spotUsers = new ArrayList<>();
}
