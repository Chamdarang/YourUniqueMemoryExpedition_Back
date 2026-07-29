package study.yume.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.dto.plan.response.PlanImportPreviewResponse;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.Transportation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralPlanImportServiceTests {

    private final GeneralPlanImportService service = new GeneralPlanImportService();

    @Test
    void infersStayDurationFromNextExplicitStartTime() {
        MockMultipartFile file = csv("""
                일차,시간,장소
                1일,09:00,센다이역
                ,11:30,센다이성
                ,14:00,즈이호덴
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config("COLUMN", "ALL", Map.of("day", 0, "start", 1, "place", 2))
        );

        List<PlanTransferDto.Schedule> schedules = preview.plan().days().get(0).schedules();
        assertThat(schedules).extracting(PlanTransferDto.Schedule::duration)
                .containsExactly(150, 150, 60);
        assertThat(schedules).extracting(PlanTransferDto.Schedule::fixedStartTime)
                .containsExactly(true, true, true);
    }

    @Test
    void placesWithoutTimesStartAtNineInOneHourIntervals() {
        MockMultipartFile file = csv("""
                장소
                센다이역
                센다이성
                즈이호덴
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config("NONE", "ALL", Map.of("place", 0))
        );

        List<PlanTransferDto.Schedule> schedules = preview.plan().days().get(0).schedules();
        assertThat(schedules).extracting(schedule -> schedule.startTime().toString())
                .containsExactly("09:00", "10:00", "11:00");
        assertThat(schedules).extracting(PlanTransferDto.Schedule::duration)
                .containsExactly(60, 60, 60);
        assertThat(schedules).extracting(PlanTransferDto.Schedule::fixedStartTime)
                .containsExactly(true, false, false);
    }

    @Test
    void expandsArrowMovementRowsIntoTheirOwnDestinations() {
        MockMultipartFile file = csv("""
                시간,장소,소요,교통
                09:00,센다이역,,
                10:00,센다이역->센다이성,30,버스
                10:30,센다이성,60,
                12:00,센다이성->공항,20,렌터카
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config(
                        "NONE",
                        "ARROW",
                        Map.of("start", 0, "place", 1, "duration", 2, "transport", 3)
                )
        );

        List<PlanTransferDto.Schedule> schedules = preview.plan().days().get(0).schedules();
        assertThat(schedules).extracting(PlanTransferDto.Schedule::spotName)
                .containsExactly("센다이역", "센다이성", "공항");
        assertThat(schedules.get(1).movingDuration()).isEqualTo(30);
        assertThat(schedules.get(1).transportation()).isEqualTo(Transportation.BUS);
        assertThat(schedules.get(2).startTime()).isEqualTo(LocalTime.of(12, 20));
        assertThat(schedules.get(2).movingDuration()).isEqualTo(20);
        assertThat(schedules.get(2).transportation()).isEqualTo(Transportation.CAR);
    }

    @Test
    void mergesActivityAfterArrowDestinationAsStaySchedule() {
        MockMultipartFile file = csv("""
                시작,목적,소요,종료,OFFSET
                05:00,집->인천공항,90,06:45,15
                06:45,출국수속,160,09:25,
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config(
                        "NONE",
                        "ARROW",
                        Map.of("start", 0, "place", 1, "duration", 2, "end", 3, "offset", 4)
                )
        );

        List<PlanTransferDto.Schedule> schedules = preview.plan().days().get(0).schedules();
        assertThat(schedules).extracting(PlanTransferDto.Schedule::spotName)
                .containsExactly("집", "인천공항");
        assertThat(schedules.get(0).duration()).isZero();
        assertThat(schedules.get(1).startTime()).isEqualTo(LocalTime.of(6, 45));
        assertThat(schedules.get(1).movingDuration()).isEqualTo(105);
        assertThat(schedules.get(1).duration()).isEqualTo(160);
        assertThat(schedules.get(1).memo()).contains("출국수속");
    }

    @Test
    void mergesSightseeingTextIntoMovementDestinationInsteadOfUsingItAsPlaceName() {
        MockMultipartFile file = csv("""
                시작,목적,소요,종료
                17:31,모리오카역->모리오카시청,25,17:56
                17:56,산사오도리축제 구경,100,19:36
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config(
                        "NONE",
                        "ARROW",
                        Map.of("start", 0, "place", 1, "duration", 2, "end", 3)
                )
        );

        List<PlanTransferDto.Schedule> schedules = preview.plan().days().get(0).schedules();
        assertThat(schedules).extracting(PlanTransferDto.Schedule::spotName)
                .containsExactly("모리오카역", "모리오카시청");
        assertThat(schedules.get(1).duration()).isEqualTo(100);
        assertThat(schedules.get(1).memo()).contains("산사오도리축제 구경");
        assertThat(schedules.get(1).spotType()).isEqualTo(study.yume.model.enums.SpotType.ACTIVITY);
    }

    @Test
    void analyzesCsvHeaderAndSamples() {
        var result = service.analyze(csv("""
                일차,시작시간,장소
                1일,09:00,센다이역
                """), "AUTO", "AUTO");

        assertThat(result.fileType()).isEqualTo("CSV");
        assertThat(result.sheets()).singleElement()
                .satisfies(sheet -> {
                    assertThat(sheet.suggestedHeaderRow()).isEqualTo(1);
                    assertThat(sheet.columns()).extracting(column -> column.label())
                            .containsExactly("일차", "시작시간", "장소");
                });
    }

    @Test
    void importsDayMemoAndSilentlyIgnoresRowsWithOnlyUnmappedValues() {
        MockMultipartFile file = csv("""
                일차,종합,목적,시작시간,소요시간,준비물
                1일,센다이 도착일,센다이역,09:00,60,
                ,,센다이성,10:30,90,
                ,,,,,상의 4벌
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config(
                        "COLUMN",
                        "ALL",
                        Map.of("day", 0, "dayMemo", 1, "place", 2, "start", 3, "duration", 4)
                )
        );

        assertThat(preview.plan().days()).singleElement()
                .satisfies(day -> {
                    assertThat(day.memo()).isEqualTo("센다이 도착일");
                    assertThat(day.schedules()).hasSize(2);
                });
        assertThat(preview.summary().sourceRows()).isEqualTo(2);
        assertThat(preview.issues()).isEmpty();
    }

    @Test
    void infersTransportationAndKeepsRawTextFromMultilineMovementRow() {
        MockMultipartFile file = csv("""
                시간,목적,소요시간
                09:00,센다이역,
                10:00,"센다이역->모리오카역
                신칸센",30
                10:30,모리오카역,60
                """);

        PlanImportPreviewResponse preview = service.preview(
                file,
                config("NONE", "ARROW", Map.of("start", 0, "place", 1, "duration", 2))
        );

        PlanTransferDto.Schedule destination = preview.plan().days().get(0).schedules().get(1);
        assertThat(destination.transportation()).isEqualTo(Transportation.TRAIN);
        assertThat(destination.movingDuration()).isEqualTo(30);
        assertThat(destination.movingMemo()).contains("센다이역->모리오카역", "신칸센");
    }

    private GeneralImportConfig config(
            String dayMode,
            String rowMode,
            Map<String, Integer> columns
    ) {
        return new GeneralImportConfig(
                "범용 Import 테스트",
                LocalDate.of(2026, 7, 28),
                List.of(),
                1,
                2,
                dayMode,
                rowMode,
                columns,
                List.of("이동"),
                "AUTO",
                "AUTO",
                LocalTime.of(9, 0),
                60,
                60,
                true,
                true,
                Map.of(),
                "AUTO",
                "AUTO"
        );
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "plan.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
