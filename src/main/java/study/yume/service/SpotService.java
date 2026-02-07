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
import study.yume.dto.spot.response.SpotResponse;
import study.yume.exception.SpotInUseException;
import study.yume.exception.UsedScheduleProjection;
import study.yume.model.Spot;
import study.yume.model.SpotUser;
import study.yume.repository.SpotRepository;
import study.yume.repository.SpotUserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SpotService {
    private final SpotRepository spotRepository;
    private final SpotUserRepository spotUserRepository;
    private final DayScheduleService dayScheduleService;

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


        if (spotUserRepository.existsByUserIdAndSpotId(userId, spot.getId())) {
            throw new IllegalArgumentException("이미 등록된 장소입니다.");
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

        if (req.customName() != null) spotUser.setCustomName(req.customName());
        if (req.spotType() != null) spotUser.setSpotType(req.spotType());
        if (req.isVisit() != null) spotUser.setIsVisit(req.isVisit());
        if (req.description() != null) spotUser.setDescription(req.description());
        if (req.metadata() != null) spotUser.setMetadata(req.metadata());
        spotUserRepository.save(spotUser);

        return SpotResponse.toDto(spotUser.getSpot(), spotUser);
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
}


