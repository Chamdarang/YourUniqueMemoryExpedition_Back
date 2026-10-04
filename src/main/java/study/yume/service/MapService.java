package study.yume.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MapService {
    @Value("${google.maps.apiKey}")
    private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] staticMap(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("지도 내보내기 API 키가 설정되지 않았습니다. GOOGLE_MAPS_API_KEY를 확인해 주세요.");
        }
        if (query == null || query.isBlank() || query.length() > 14_000) {
            throw new IllegalArgumentException("정적 지도 요청 정보가 비어 있거나 너무 깁니다.");
        }
        String url = "https://maps.googleapis.com/maps/api/staticmap?"
                + query
                + "&language=ko"
                + "&region=KR"
                + "&key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.IMAGE_PNG, MediaType.ALL));

        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class
            );
            MediaType contentType = resp.getHeaders().getContentType();
            if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
                throw new IllegalStateException("Google Maps Static API가 이미지가 아닌 응답을 반환했습니다.");
            }
            return resp.getBody();
        } catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.warn("google_static_map_error status={} body={}", exception.getStatusCode(), responseBody);
            throw new IllegalStateException(
                    "Google Maps Static API 호출에 실패했습니다. API 활성화, 결제 계정, GOOGLE_MAPS_API_KEY 제한과 EC2 공인 IP를 확인해 주세요.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("Google 지도 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
    }
}
