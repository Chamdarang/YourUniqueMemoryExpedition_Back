package study.yume.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import study.yume.dto.route.request.RouteEstimateRequest;
import study.yume.dto.route.response.RouteEstimateResponse;
import study.yume.exception.RateLimitExceededException;
import study.yume.model.enums.Transportation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RouteEstimateService {

    private static final String FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline";

    private final RestClient restClient;
    private final RestClient navitimeRestClient;
    private final String apiKey;
    private final String navitimeApiKey;
    private final long cacheTtlMillis;
    private final int cacheMaxEntries;
    private final int rateLimit;
    private final long rateWindowMillis;
    private final ConcurrentHashMap<RouteCacheKey, CachedRoute> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RouteCacheKey, Object> routeLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Deque<Long>> userRequests = new ConcurrentHashMap<>();

    public RouteEstimateService(
            RestClient.Builder restClientBuilder,
            @Value("${google.maps.routes-api-key:}") String apiKey,
            @Value("${navitime.rapid-api-key:${NAVITIME_RAPID_API_KEY:}}") String navitimeApiKey,
            @Value("${route.estimate.cache-ttl-seconds:600}") long cacheTtlSeconds,
            @Value("${route.estimate.cache-max-entries:500}") int cacheMaxEntries,
            @Value("${route.estimate.rate-limit:60}") int rateLimit,
            @Value("${route.estimate.rate-window-seconds:600}") long rateWindowSeconds
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(20_000);
        restClientBuilder.requestFactory(requestFactory);
        this.restClient = restClientBuilder
                .baseUrl("https://routes.googleapis.com")
                .build();
        this.navitimeRestClient = restClientBuilder
                .baseUrl("https://navitime-route-totalnavi.p.rapidapi.com")
                .build();
        this.apiKey = apiKey;
        this.navitimeApiKey = navitimeApiKey;
        this.cacheTtlMillis = Math.max(1, cacheTtlSeconds) * 1_000L;
        this.cacheMaxEntries = Math.max(10, cacheMaxEntries);
        this.rateLimit = Math.max(1, rateLimit);
        this.rateWindowMillis = Math.max(1, rateWindowSeconds) * 1_000L;
    }

    public RouteEstimateResponse estimate(Long userId, RouteEstimateRequest request) {
        validate(request);
        if (userId == null) throw new IllegalArgumentException("로그인이 필요합니다.");

        long now = System.currentTimeMillis();
        RouteCacheKey cacheKey = RouteCacheKey.from(request);
        CachedRoute cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) return cached.response();

        Object lock = routeLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        try {
            synchronized (lock) {
                now = System.currentTimeMillis();
                cached = cache.get(cacheKey);
                if (cached != null && cached.expiresAt() > now) return cached.response();

                checkRateLimit(userId, now);
                RouteEstimateResponse response = estimateFromProvider(request);
                cache.put(cacheKey, new CachedRoute(response, now + cacheTtlMillis));
                trimCache(now);
                return response;
            }
        } finally {
            routeLocks.remove(cacheKey, lock);
        }
    }

    private RouteEstimateResponse estimateFromProvider(RouteEstimateRequest request) {
        if (request.transportation() == Transportation.TRAIN) {
            return estimateWithNavitime(request);
        }
        if (request.transportation() == Transportation.BUS) {
            throw new IllegalArgumentException(
                    "무료 NAVITIME 플랜은 일반 노선버스를 지원하지 않습니다. Google 지도에서 확인 후 직접 입력해 주세요."
            );
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_MAPS_ROUTES_API_KEY 환경 변수가 설정되지 않았습니다."
            );
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(createRequestBody(request))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "route_provider_error provider=google status={} transportation={}",
                    exception.getStatusCode().value(),
                    request.transportation(),
                    exception
            );
            throw new IllegalStateException(
                    "Google 경로 API 호출에 실패했습니다. Routes API 활성화, API 키 제한, 할당량을 확인해 주세요.",
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("route_provider_connection_error provider=google transportation={}", request.transportation(), exception);
            throw new IllegalStateException("Google 경로 API 연결 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.", exception);
        }

        JsonNode route = response == null ? null : response.path("routes").path(0);
        if (route == null || route.isMissingNode()) {
            throw new IllegalArgumentException("선택한 이동수단으로 계산 가능한 경로가 없습니다.");
        }

        return new RouteEstimateResponse(
                parseDurationMinutes(route.path("duration").asText()),
                route.path("distanceMeters").asInt(),
                route.path("polyline").path("encodedPolyline").asText(),
                ""
        );
    }

    private void checkRateLimit(Long userId, long now) {
        Deque<Long> requests = userRequests.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (requests) {
            long cutoff = now - rateWindowMillis;
            while (!requests.isEmpty() && requests.peekFirst() < cutoff) requests.removeFirst();
            if (requests.size() >= rateLimit) {
                long retryAfterSeconds = Math.max(
                        1,
                        (requests.peekFirst() + rateWindowMillis - now + 999) / 1_000
                );
                throw new RateLimitExceededException(
                        "경로 계산 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                        retryAfterSeconds
                );
            }
            requests.addLast(now);
        }
    }

    private void trimCache(long now) {
        if (cache.size() <= cacheMaxEntries) return;
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        while (cache.size() > cacheMaxEntries) {
            var oldest = cache.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().expiresAt()))
                    .orElse(null);
            if (oldest == null) break;
            cache.remove(oldest.getKey());
        }
    }

    private RouteEstimateResponse estimateWithNavitime(RouteEstimateRequest request) {
        if (navitimeApiKey.isBlank()) {
            throw new IllegalStateException("NAVITIME_RAPID_API_KEY 환경 변수가 설정되지 않았습니다.");
        }

        String startTime = request.departureTime() == null || request.departureTime().isBlank()
                ? LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
                    .withSecond(0)
                    .withNano(0)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : request.departureTime();

        JsonNode response;
        try {
            response = navitimeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route_transit")
                            .queryParam("start", request.originLat() + "," + request.originLng())
                            .queryParam("goal", request.destinationLat() + "," + request.destinationLng())
                            .queryParam("start_time", startTime)
                            .queryParam("shape", false)
                            .build())
                    .header("X-RapidAPI-Key", navitimeApiKey)
                    .header("X-RapidAPI-Host", "navitime-route-totalnavi.p.rapidapi.com")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "route_provider_error provider=navitime status={} transportation={}",
                    exception.getStatusCode().value(),
                    request.transportation(),
                    exception
            );
            String message = exception.getStatusCode().value() == 429
                    ? "NAVITIME 무료 월간 호출 한도를 초과했습니다."
                    : "NAVITIME API 호출에 실패했습니다. RapidAPI 구독과 API 키를 확인해 주세요.";
            throw new IllegalStateException(
                    message,
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("route_provider_connection_error provider=navitime transportation={}", request.transportation(), exception);
            throw new IllegalStateException("NAVITIME API 연결 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.", exception);
        }

        JsonNode route = response == null ? null : response.path("items").path(0);
        JsonNode move = route == null ? null : route.path("summary").path("move");
        if (move == null || move.isMissingNode()) {
            throw new IllegalArgumentException("NAVITIME에서 계산 가능한 열차 경로를 찾지 못했습니다.");
        }

        return new RouteEstimateResponse(
                move.path("time").asInt(),
                move.path("distance").asInt(),
                "",
                buildNavitimeMovingMemo(route)
        );
    }

    static String buildNavitimeMovingMemo(JsonNode route) {
        JsonNode sections = route == null ? null : route.path("sections");
        if (sections == null || !sections.isArray()) {
            return "";
        }

        List<String> steps = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            JsonNode section = sections.path(index);
            if (!"move".equals(section.path("type").asText())) {
                continue;
            }

            String from = pointName(sections, index - 1);
            String to = pointName(sections, index + 1);
            String lineName = firstText(
                    section.path("transport").path("self_name"),
                    section.path("line_name")
            );
            String moveType = section.path("move").asText();
            String transportType = section.path("transport").path("type").asText();
            String destination = section.path("transport").path("destination").path("name").asText();
            String fromTime = formatNavitimeTime(section.path("from_time").asText());
            String toTime = formatNavitimeTime(section.path("to_time").asText());
            int minutes = section.path("time").asInt();

            boolean walk = section.path("transport").isMissingNode()
                    || section.path("transport").isNull()
                    || "walk".equalsIgnoreCase(moveType)
                    || "徒歩".equals(lineName);

            if (walk) {
                String places = from.isBlank() || to.isBlank() ? "" : ": " + from + " → " + to;
                steps.add("도보 " + minutes + "분" + places);
                continue;
            }

            StringBuilder step = new StringBuilder();
            if (!fromTime.isBlank()) {
                step.append(fromTime).append(" ");
            }
            if (!from.isBlank()) {
                step.append(from).append("에서 ");
            }
            step.append(lineName.isBlank() ? "열차" : lineName);
            if (!transportType.isBlank() && !step.toString().contains(transportType)) {
                step.append(" ").append(transportType);
            }
            if (!destination.isBlank()) {
                step.append(" (").append(destination).append(" 방면)");
            }
            step.append(" 탑승");
            if (!to.isBlank()) {
                step.append(" → ");
                if (!toTime.isBlank()) {
                    step.append(toTime).append(" ");
                }
                step.append(to).append(" 하차");
            }
            steps.add(step.toString());
        }

        String memo = String.join("\n", steps);
        return memo.length() <= 500 ? memo : memo.substring(0, 497) + "...";
    }

    private static String pointName(JsonNode sections, int index) {
        if (index < 0 || index >= sections.size()) {
            return "";
        }
        String name = sections.path(index).path("name").asText();
        return switch (name) {
            case "start" -> "출발지";
            case "goal" -> "도착지";
            default -> name;
        };
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return "";
    }

    private static String formatNavitimeTime(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return "";
        }
        int separator = dateTime.indexOf('T');
        if (separator >= 0 && dateTime.length() >= separator + 6) {
            return dateTime.substring(separator + 1, separator + 6);
        }
        return dateTime.length() >= 5 ? dateTime.substring(0, 5) : dateTime;
    }

    private Map<String, Object> createRequestBody(RouteEstimateRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", waypoint(request.originLat(), request.originLng()));
        body.put("destination", waypoint(request.destinationLat(), request.destinationLng()));
        body.put("travelMode", toGoogleTravelMode(request.transportation()));
        body.put("languageCode", "ko");
        body.put("units", "METRIC");
        List<String> allowedTransitModes = toGoogleAllowedTransitModes(request.transportation());
        if (!allowedTransitModes.isEmpty()) {
            body.put("transitPreferences", Map.of("allowedTravelModes", allowedTransitModes));
        }

        return body;
    }

    private Map<String, Object> waypoint(double latitude, double longitude) {
        return Map.of(
                "location", Map.of(
                        "latLng", Map.of(
                                "latitude", latitude,
                                "longitude", longitude
                        )
                )
        );
    }

    static String toGoogleTravelMode(Transportation transportation) {
        return switch (transportation) {
            case WALK -> "WALK";
            case BUS, TRAIN -> "TRANSIT";
            case TAXI, CAR -> "DRIVE";
            case BICYCLE -> "BICYCLE";
            case MOTORCYCLE -> "TWO_WHEELER";
            case SHIP, AIRPLANE ->
                    throw new IllegalArgumentException("배와 항공 이동은 자동 경로 계산을 지원하지 않습니다.");
        };
    }

    static List<String> toGoogleAllowedTransitModes(Transportation transportation) {
        return switch (transportation) {
            case BUS -> List.of("BUS");
            case TRAIN -> List.of("TRAIN");
            default -> List.of();
        };
    }

    static int parseDurationMinutes(String duration) {
        if (duration == null || duration.isBlank() || !duration.endsWith("s")) {
            throw new IllegalArgumentException("Google 경로 응답의 소요시간 형식이 올바르지 않습니다.");
        }
        BigDecimal seconds = new BigDecimal(duration.substring(0, duration.length() - 1));
        return seconds.divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING).intValue();
    }

    private record CachedRoute(RouteEstimateResponse response, long expiresAt) {
    }

    private record RouteCacheKey(
            long originLat,
            long originLng,
            long destinationLat,
            long destinationLng,
            Transportation transportation,
            String departureMinute
    ) {
        private static RouteCacheKey from(RouteEstimateRequest request) {
            String departure = request.transportation() == Transportation.TRAIN && request.departureTime() != null
                    ? request.departureTime().trim()
                    : "";
            if (departure.length() > 16) departure = departure.substring(0, 16);
            return new RouteCacheKey(
                    roundedCoordinate(request.originLat()),
                    roundedCoordinate(request.originLng()),
                    roundedCoordinate(request.destinationLat()),
                    roundedCoordinate(request.destinationLng()),
                    request.transportation(),
                    departure
            );
        }

        private static long roundedCoordinate(double value) {
            return Math.round(value * 100_000D);
        }
    }

    private void validate(RouteEstimateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("경로 계산 요청 정보가 없습니다.");
        }
        if (request.transportation() == null) {
            throw new IllegalArgumentException("이동수단을 선택해 주세요.");
        }
        List<Double> coordinates = List.of(
                request.originLat(),
                request.originLng(),
                request.destinationLat(),
                request.destinationLng()
        );
        if (coordinates.stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("출발지 또는 도착지 좌표가 올바르지 않습니다.");
        }
        if (Math.abs(request.originLat()) > 90 || Math.abs(request.destinationLat()) > 90
                || Math.abs(request.originLng()) > 180 || Math.abs(request.destinationLng()) > 180) {
            throw new IllegalArgumentException("출발지 또는 도착지 좌표 범위를 확인해 주세요.");
        }
    }
}
