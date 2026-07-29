package study.yume.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import study.yume.model.Spot;
import study.yume.model.SpotUser;
import study.yume.model.enums.SpotType;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.SpotVisitHistoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotServiceTests {

    private final SpotRepository spotRepository = mock(SpotRepository.class);
    private final SpotUserRepository spotUserRepository = mock(SpotUserRepository.class);
    private final DayScheduleService dayScheduleService = mock(DayScheduleService.class);
    private final DayScheduleRepository dayScheduleRepository = mock(DayScheduleRepository.class);
    private final SpotPurchaseRepository spotPurchaseRepository = mock(SpotPurchaseRepository.class);
    private final SpotVisitHistoryRepository spotVisitHistoryRepository =
            mock(SpotVisitHistoryRepository.class);
    private final SpotService service = new SpotService(
            spotRepository,
            spotUserRepository,
            dayScheduleService,
            dayScheduleRepository,
            spotPurchaseRepository,
            spotVisitHistoryRepository
    );

    @BeforeEach
    void resetRepositoryDefaults() {
        when(spotUserRepository.findAllByUserId(1L)).thenReturn(List.of());
    }

    @Test
    void onlySuggestsSameNamedSpotsWhenTheirLocationsAreClose() {
        SpotUser nearFirst = spotUser(10L, "도후쿠지", 35.0000, 135.0000);
        SpotUser nearSecond = spotUser(11L, "도후쿠지", 35.0003, 135.0003);
        SpotUser farAway = spotUser(12L, "도후쿠지", 34.6800, 135.8300);
        when(spotUserRepository.findAllByUserId(1L))
                .thenReturn(List.of(nearFirst, nearSecond, farAway));

        var candidates = service.findDuplicateCandidates(1L);

        assertThat(candidates).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.first().id()).isEqualTo(10L);
                    assertThat(candidate.second().id()).isEqualTo(11L);
                });
    }

    @Test
    void mergeMovesRelatedDataBeforeDeletingSource() {
        SpotUser target = spotUser(10L, "남길 장소", 35.0, 135.0);
        SpotUser source = spotUser(11L, "합칠 장소", 35.0, 135.0);
        target.setDescription("남길 장소 메모");
        target.setMetadata(Map.of("targetOnly", "target", "shared", "target"));
        source.setIsVisit(true);
        source.setDescription("합칠 장소 메모");
        source.setMetadata(Map.of("sourceOnly", "source", "shared", "source"));
        when(spotUserRepository.findByUserIdAndId(1L, 10L)).thenReturn(Optional.of(target));
        when(spotUserRepository.findByUserIdAndId(1L, 11L)).thenReturn(Optional.of(source));

        service.mergeSpots(1L, 10L, 11L);

        assertThat(target.getIsVisit()).isTrue();
        assertThat(target.getDescription()).isEqualTo("남길 장소 메모\n\n합칠 장소 메모");
        assertThat(target.getMetadata()).containsEntry("targetOnly", "target");
        assertThat(target.getMetadata()).containsEntry("sourceOnly", "source");
        assertThat(target.getMetadata()).containsEntry("shared", "target");
        verify(dayScheduleRepository).reassignSpotUser(1L, 11L, target);
        verify(spotPurchaseRepository).reassignSpotUser(1L, 11L, target);
        verify(spotVisitHistoryRepository).reassignSpotUser(1L, 11L, target);
        verify(spotUserRepository).delete(source);
    }

    private SpotUser spotUser(Long id, String name, double lat, double lng) {
        Spot spot = new Spot();
        spot.setId(id + 100);
        spot.setSpotName(name);
        spot.setAddress("");
        spot.setLocation(new GeometryFactory().createPoint(new Coordinate(lng, lat)));
        spot.setMetadata(Map.of());

        SpotUser spotUser = new SpotUser();
        spotUser.setId(id);
        spotUser.setUserId(1L);
        spotUser.setSpot(spot);
        spotUser.setCustomName(name);
        spotUser.setSpotType(SpotType.OTHER);
        spotUser.setIsVisit(false);
        return spotUser;
    }
}
