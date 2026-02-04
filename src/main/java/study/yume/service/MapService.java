package study.yume.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MapService {
    @Value("${google.maps.apiKey}")
    private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] staticMap(String query) {
        String url = "https://maps.googleapis.com/maps/api/staticmap?"
                + query
                + "&language=ko"
                + "&region=KR"
                + "&key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.IMAGE_PNG, MediaType.ALL));

        ResponseEntity<byte[]> resp = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        return resp.getBody();
    }



}
