package study.yume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import study.yume.model.enums.Transportation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteEstimateServiceTests {

    @Test
    void mapsYumeTransportationToGoogleTravelMode() {
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.WALK)).isEqualTo("WALK");
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.BUS)).isEqualTo("TRANSIT");
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.TRAIN)).isEqualTo("TRANSIT");
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.TAXI)).isEqualTo("DRIVE");
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.BICYCLE)).isEqualTo("BICYCLE");
        assertThat(RouteEstimateService.toGoogleTravelMode(Transportation.MOTORCYCLE))
                .isEqualTo("TWO_WHEELER");
    }

    @Test
    void rejectsUnsupportedTransportation() {
        assertThatThrownBy(() -> RouteEstimateService.toGoogleTravelMode(Transportation.AIRPLANE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteEstimateService.toGoogleTravelMode(Transportation.SHIP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsTransitTransportationToGoogleTransitPreferences() {
        assertThat(RouteEstimateService.toGoogleAllowedTransitModes(Transportation.BUS))
                .containsExactly("BUS");
        assertThat(RouteEstimateService.toGoogleAllowedTransitModes(Transportation.TRAIN))
                .containsExactly("TRAIN");
        assertThat(RouteEstimateService.toGoogleAllowedTransitModes(Transportation.WALK))
                .isEmpty();
    }

    @Test
    void roundsDurationUpToWholeMinutes() {
        assertThat(RouteEstimateService.parseDurationMinutes("60s")).isEqualTo(1);
        assertThat(RouteEstimateService.parseDurationMinutes("61s")).isEqualTo(2);
        assertThat(RouteEstimateService.parseDurationMinutes("125.4s")).isEqualTo(3);
    }

    @Test
    void buildsMovingMemoFromNavitimeSections() throws Exception {
        JsonNode route = new ObjectMapper().readTree("""
                {
                  "sections": [
                    {"type":"point","name":"start"},
                    {"type":"move","move":"walk","line_name":"徒歩","time":8},
                    {"type":"point","name":"京都"},
                    {
                      "type":"move",
                      "move":"train",
                      "line_name":"JR奈良線",
                      "from_time":"2026-07-28T09:12:00",
                      "to_time":"2026-07-28T09:15:00",
                      "time":3,
                      "transport":{
                        "type":"普通",
                        "destination":{"name":"奈良"}
                      }
                    },
                    {"type":"point","name":"東福寺"},
                    {"type":"move","move":"walk","line_name":"徒歩","time":10},
                    {"type":"point","name":"goal"}
                  ]
                }
                """);

        assertThat(RouteEstimateService.buildNavitimeMovingMemo(route)).isEqualTo("""
                도보 8분: 출발지 → 京都
                09:12 京都에서 JR奈良線 普通 (奈良 방면) 탑승 → 09:15 東福寺 하차
                도보 10분: 東福寺 → 도착지""");
    }
}
