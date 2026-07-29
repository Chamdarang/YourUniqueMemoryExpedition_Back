package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.dto.spot.request.SpotCreateRequest;
import study.yume.dto.spot.request.SpotGetFilteredRequest;
import study.yume.dto.spot.request.SpotUpdateRequest;
import study.yume.dto.spot.response.SpotDetailResponse;
import study.yume.dto.spot.response.SpotDuplicateCandidateResponse;
import study.yume.dto.spot.response.SpotResponse;
import study.yume.exception.SpotInUseException;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.Spot;
import study.yume.model.SpotUser;
import study.yume.repository.SpotRepository;
import study.yume.repository.SpotUserRepository;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotVisitHistoryRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class SpotService {
    private final SpotRepository spotRepository;
    private final SpotUserRepository spotUserRepository;
    private final DayScheduleService dayScheduleService;
    private final DayScheduleRepository dayScheduleRepository;
    private final SpotPurchaseRepository spotPurchaseRepository;
    private final SpotVisitHistoryRepository spotVisitHistoryRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);


    public SpotResponse createSpot(Long userId, SpotCreateRequest req) {
        Spot spot = spotRepository.findByPlaceId(req.placeId())
                .orElseGet(() -> {
                    Spot newSpot = new Spot();
                    newSpot.setPlaceId(req.placeId());
                    newSpot.setSpotName(req.spotName());
                    newSpot.setAddress(req.address());
                    newSpot.setShortAddress(req.shortAddress());
                    newSpot.setWebsite(req.website());
                    newSpot.setGoogleMapUrl(req.googleMapUrl());

                    Point location = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
                    newSpot.setLocation(location);
                    newSpot.setMetadata(req.metadata());

                    return spotRepository.save(newSpot);
                });


        SpotUser existingSpotUser = spotUserRepository.findByUserIdAndSpotId(userId, spot.getId())
                .orElse(null);
        if (existingSpotUser != null) {
            return SpotResponse.toDto(spot, existingSpotUser);
        }

        SpotUser spotUser = new SpotUser();
        spotUser.setUserId(userId);
        spotUser.setSpot(spot);
        spotUser.setSpotType(req.spotType());
        spotUser.setCustomName(req.spotName());
        spotUser.setIsVisit(req.isVisit() != null ? req.isVisit() : false);
        spotUser.setDescription(req.description());
        //spotUser.setMetadata(req.userMetadata());
        spotUserRepository.save(spotUser);

        return SpotResponse.toDto(spot, spotUser);
    }

    @Transactional(readOnly = true)
    public Page<SpotResponse> getFilteredSpots(Long userId, SpotGetFilteredRequest req, Pageable pageable) {
        Page<SpotUser> spotUserPage;

        if (req.lat() != null && req.lng() != null && req.radius() != null) {
            Point center = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
            spotUserPage = spotUserRepository.findWithLocation(userId, req.isVisit(), req.spotType(), center, req.radius(), pageable);
        } else {
            spotUserPage = spotUserRepository.searchMySpotUsers(userId, req.keyword(), req.spotType(), req.isVisit(), pageable);
        }

        return spotUserPage.map(spotUser -> SpotResponse.toDto(spotUser.getSpot(), spotUser));

    }

    @Transactional(readOnly = true)
    public SpotDetailResponse getSpotById(Long userId, Long spotUserId) {
        SpotUser spotUser = findSpotUserByUserIdAndId(userId, spotUserId);
        Spot spot = spotUser.getSpot();
        return SpotDetailResponse.toDto(spot, spotUser);
    }

    public SpotResponse updateSpot(Long userId, Long spotUserId, SpotUpdateRequest req) {
        SpotUser spotUser = findSpotUserByUserIdAndId(userId, spotUserId);

        if (req.spotName() != null) spotUser.setCustomName(req.spotName());
        if (req.spotType() != null) spotUser.setSpotType(req.spotType());
        if (req.isVisit() != null) spotUser.setIsVisit(req.isVisit());
        if (req.description() != null) spotUser.setDescription(req.description());
        if (req.metadata() != null) spotUser.setMetadata(req.metadata());
        spotUserRepository.save(spotUser);

        return SpotResponse.toDto(spotUser.getSpot(), spotUser);
    }

    @Transactional(readOnly = true)
    public List<SpotDuplicateCandidateResponse> findDuplicateCandidates(Long userId) {
        List<SpotUser> spots = spotUserRepository.findAllByUserId(userId);
        List<SpotDuplicateCandidateResponse> candidates = new ArrayList<>();

        for (int firstIndex = 0; firstIndex < spots.size(); firstIndex++) {
            SpotUser first = spots.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < spots.size(); secondIndex++) {
                SpotUser second = spots.get(secondIndex);
                String firstName = normalizeName(displayName(first));
                String secondName = normalizeName(displayName(second));
                int distance = distanceMeters(first.getSpot().getLocation(), second.getSpot().getLocation());

                String reason = null;
                if (!firstName.isBlank() && firstName.equals(secondName) && distance <= 500) {
                    reason = "이름이 같고 위치가 가깝습니다.";
                } else if (distance <= 100 && namesOverlap(firstName, secondName)) {
                    reason = "이름이 비슷하고 위치가 가깝습니다.";
                }
                if (reason != null) {
                    candidates.add(new SpotDuplicateCandidateResponse(
                            SpotResponse.toDto(first.getSpot(), first),
                            SpotResponse.toDto(second.getSpot(), second),
                            reason,
                            distance
                    ));
                }
            }
        }
        return candidates;
    }

    public SpotResponse mergeSpots(Long userId, Long targetId, Long sourceId) {
        if (targetId.equals(sourceId)) {
            throw new IllegalArgumentException("서로 다른 두 장소를 선택해 주세요.");
        }
        SpotUser target = findSpotUserByUserIdAndId(userId, targetId);
        SpotUser source = findSpotUserByUserIdAndId(userId, sourceId);

        target.setIsVisit(Boolean.TRUE.equals(target.getIsVisit()) || Boolean.TRUE.equals(source.getIsVisit()));
        target.setDescription(mergeDescription(target.getDescription(), source.getDescription()));
        target.setMetadata(mergeMetadata(target.getMetadata(), source.getMetadata()));

        Set<Long> targetGroupIds = new HashSet<>();
        target.getSpotGroup().forEach(group -> targetGroupIds.add(group.getId()));
        source.getSpotGroup().stream()
                .filter(group -> !targetGroupIds.contains(group.getId()))
                .forEach(target.getSpotGroup()::add);
        source.getSpotGroup().clear();
        spotUserRepository.saveAndFlush(target);
        spotUserRepository.saveAndFlush(source);

        dayScheduleRepository.reassignSpotUser(userId, sourceId, target);
        spotPurchaseRepository.reassignSpotUser(userId, sourceId, target);
        spotVisitHistoryRepository.reassignSpotUser(userId, sourceId, target);
        spotUserRepository.flush();
        spotUserRepository.delete(source);

        return SpotResponse.toDto(target.getSpot(), target);
    }

    public void deleteSpot(Long userId, Long spotUserId) {
        SpotUser spotUser = findSpotUserByUserIdAndId(userId, spotUserId);
        List<UsedScheduleProjection> usage = dayScheduleService.findUsageBySpotId(userId, spotUserId);
        if (!usage.isEmpty()) {
            throw new SpotInUseException(usage);
        }
        spotUserRepository.delete(spotUser);
    }

    public void spotDataUpdate(String placeId, SpotCreateRequest req) {
        Spot spot = spotRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
        spot.setSpotName(req.spotName());
        spot.setAddress(req.address());
        spot.setShortAddress(req.shortAddress());
        spot.setWebsite(req.website());
        spot.setGoogleMapUrl(req.googleMapUrl());

        Point location = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
        spot.setLocation(location);
        spot.setMetadata(req.metadata());

        spotRepository.save(spot);

    }

    private SpotUser findSpotUserByUserIdAndId(Long userId, Long spotUserId) {
        return spotUserRepository.findByUserIdAndId(userId, spotUserId)
                .orElseThrow(() -> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
    }

    private Spot findSpotBydId(Long spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("공용 장소 정보를 찾을 수 없습니다."));
    }

    private String displayName(SpotUser spotUser) {
        return spotUser.getCustomName() == null || spotUser.getCustomName().isBlank()
                ? spotUser.getSpot().getSpotName()
                : spotUser.getCustomName();
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·ㆍ]+", "");
    }

    private boolean namesOverlap(String first, String second) {
        return first.length() >= 3
                && second.length() >= 3
                && (first.contains(second) || second.contains(first));
    }

    private String mergeDescription(String target, String source) {
        if (source == null || source.isBlank()) return target;
        if (target == null || target.isBlank()) return source;
        if (target.trim().equals(source.trim())) return target;
        return target.stripTrailing() + "\n\n" + source.strip();
    }

    private Map<String, Object> mergeMetadata(
            Map<String, Object> target,
            Map<String, Object> source
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (source != null) merged.putAll(source);
        if (target != null) merged.putAll(target);
        return merged;
    }

    private int distanceMeters(Point first, Point second) {
        double earthRadius = 6_371_000;
        double lat1 = Math.toRadians(first.getY());
        double lat2 = Math.toRadians(second.getY());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(second.getX() - first.getX());
        double value = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return (int) Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value)));
    }
}


