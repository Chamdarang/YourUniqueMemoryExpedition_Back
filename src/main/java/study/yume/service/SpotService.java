package study.yume.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
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
import study.yume.repository.SpotRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SpotService {
    private final SpotRepository spotRepository;
    private final DayScheduleService dayScheduleService;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);


    public SpotResponse createSpot(Long userId, SpotCreateRequest req) {
        Point location = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
        if(req.placeId() != null) {
            if(spotRepository.existsByUserIdAndPlaceId(userId,req.placeId())){
                throw new IllegalArgumentException("이미 등록된 장소입니다");
            }
        }else{
            if(spotRepository.existsByUserIdAndLocationAndSpotName(userId,location,req.spotName())){
                throw new IllegalArgumentException("이미 동일한 위치의 동일한 장소가 등록되어 있습니다");
            }
        }


        Spot spot = new Spot();
        spot.setUserId(userId);
        spot.setPlaceId(req.placeId());
        spot.setSpotName(req.spotName());
        spot.setSpotType(req.spotType());
        spot.setAddress(req.address());
        spot.setShortAddress(req.shortAddress());
        spot.setWebsite(req.website());
        spot.setGoogleMapUrl(req.googleMapUrl());
        spot.setLocation(location);
        spot.setIsVisit(req.isVisit() != null ? req.isVisit() : false);
        spot.setDescription(req.description());
        spot.setMetadata(req.metadata());

        return SpotResponse.toDto(spotRepository.save(spot));
    }

    @Transactional(readOnly = true)
    public List<SpotResponse> getAllSpots(Long userId) {
        return spotRepository.findALLByUserId(userId).stream()
                .map(SpotResponse::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpotResponse> getFilteredSpots(Long userId, SpotGetFilteredRequest req){
        if(req.lat()!=null && req.lng()!=null && req.radius() !=null){
            Point center = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
            return spotRepository.findSpotsWithFilters(userId,req.isVisit(),center,req.radius()).stream()
                    .map(SpotResponse::toDto)
                    .toList();
        }else if(req.isVisit()!=null){
            return spotRepository.findAllByUserIdAndIsVisit(userId,req.isVisit()).stream()
                    .map(SpotResponse::toDto)
                    .toList();
        }else{
            return getAllSpots(userId);
        }

    }

    @Transactional(readOnly = true)
    public List<SpotResponse> getSpotsByName(Long userId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return spotRepository.findByUserIdAndSpotNameContains(userId,query).stream()
                .map(SpotResponse::toDto)
                .toList();
    }


    @Transactional(readOnly = true)
    public SpotDetailResponse getSpotById(Long userId, Long spotId) {
        Spot spot = spotRepository.findDetailByUserIdAndId(userId,spotId)
                .orElseThrow(()-> new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
        return SpotDetailResponse.toDto(spot);
    }

    public SpotResponse updateSpot(Long userId, Long spotId, SpotUpdateRequest req) {
        Spot spot = findSpotByUserIdAndId(userId,spotId);

        if(req.spotName()!=null) spot.setSpotName(req.spotName());
        if(req.spotType()!=null) spot.setSpotType(req.spotType());
        if(req.address()!=null) spot.setAddress(req.address());
        if(req.shortAddress()!=null) spot.setShortAddress(req.shortAddress());
        if(req.website()!=null) spot.setWebsite(req.website());
        if(req.googleMapUrl()!=null) spot.setGoogleMapUrl(req.googleMapUrl());
        if(req.isVisit()!=null) spot.setIsVisit(req.isVisit());
        if(req.description()!=null) spot.setDescription(req.description());
        if(req.metadata()!=null) spot.setMetadata(req.metadata());
        if(req.lat()!=null && req.lng()!=null){
            Point location = geometryFactory.createPoint(new Coordinate(req.lng(), req.lat()));
            spot.setLocation(location);
        }

        return SpotResponse.toDto(spotRepository.save(spot));
    }

    public void deleteSpot(Long userId, Long spotId) {
        Spot spot = findSpotByUserIdAndId(userId,spotId);
        List<UsedScheduleProjection> usage =  dayScheduleService.findUsageBySpotId(userId,spotId);
        if (!usage.isEmpty()) {
            throw new SpotInUseException(usage);
        }
        spotRepository.delete(spot);
    }

    private Spot findSpotByUserIdAndId(Long userId, Long spotId) {
        return spotRepository.findByUserIdAndId(userId,spotId)
                .orElseThrow(()->new EntityNotFoundException("해당 장소를 찾을 수 없습니다."));
    }
}