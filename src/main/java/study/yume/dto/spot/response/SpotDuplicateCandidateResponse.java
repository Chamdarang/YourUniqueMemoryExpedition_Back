package study.yume.dto.spot.response;

public record SpotDuplicateCandidateResponse(
        SpotResponse first,
        SpotResponse second,
        String reason,
        Integer distanceMeters
) {
}
