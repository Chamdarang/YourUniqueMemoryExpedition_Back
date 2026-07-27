package study.yume.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import study.yume.dto.plan.transfer.PlanTransferDto;
import study.yume.model.enums.Transportation;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PlanSpreadsheetImportServiceTests {

    private final PlanSpreadsheetImportService service = new PlanSpreadsheetImportService();

    @Test
    void parsesScheduleTableAndMergesMovementWithFollowingActivity() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("일정");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("일자");
            header.createCell(1).setCellValue("종합");
            header.createCell(2).setCellValue("목적");
            header.createCell(3).setCellValue("시작시간");
            header.createCell(4).setCellValue("소요시간");
            header.createCell(5).setCellValue("종료시간");
            header.createCell(6).setCellValue("비고");

            Row movement = sheet.createRow(1);
            movement.createCell(0).setCellValue("4월");
            movement.createCell(1).setCellValue("[목적]\n센다이역 구경");
            movement.createCell(2).setCellValue("숙소->센다이역\nJR센세키선");
            movement.createCell(3).setCellValue(8.0 / 24);
            movement.createCell(4).setCellValue(30.0 / (24 * 60));
            movement.createCell(5).setCellValue(8.75 / 24);

            Row activity = sheet.createRow(2);
            activity.createCell(2).setCellValue("구경");
            activity.createCell(3).setCellValue(8.75 / 24);
            activity.createCell(4).setCellValue(60.0 / (24 * 60));
            activity.createCell(5).setCellValue(10.0 / 24);
            activity.createCell(6).setCellValue("역 내부 관람");

            Row secondDay = sheet.createRow(3);
            secondDay.createCell(0).setCellValue("5화");
            secondDay.createCell(1).setCellValue("[목적]\n마츠시마");
            secondDay.createCell(2).setCellValue("자유 일정");
            secondDay.createCell(3).setCellValue(10.0 / 24);
            secondDay.createCell(4).setCellValue(120.0 / (24 * 60));
            secondDay.createCell(5).setCellValue(12.0 / 24);

            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "센다이.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        );
        PlanTransferDto result = service.preview(
                file,
                "센다이 여행",
                LocalDate.of(2025, 8, 4)
        );

        assertThat(result.planDays()).isEqualTo(2);
        assertThat(result.planEndDate()).isEqualTo(LocalDate.of(2025, 8, 5));
        assertThat(result.days().get(0).schedules()).hasSize(2);

        PlanTransferDto.Schedule station = result.days().get(0).schedules().get(1);
        assertThat(station.spotName()).isEqualTo("센다이역");
        assertThat(station.transportation()).isEqualTo(Transportation.TRAIN);
        assertThat(station.movingDuration()).isEqualTo(45);
        assertThat(station.extraMovingDuration()).isEqualTo(15);
        assertThat(station.duration()).isEqualTo(75);
        assertThat(station.extraDuration()).isEqualTo(15);
        assertThat(station.memo()).contains("구경", "역 내부 관람");
    }

    @Test
    void marksImportedScheduleAsFixedWhenThereIsWaitingTimeBeforeDeparture() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("일정");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("일자");
            header.createCell(1).setCellValue("종합");
            header.createCell(2).setCellValue("목적");
            header.createCell(3).setCellValue("시작시간");
            header.createCell(4).setCellValue("소요시간");
            header.createCell(5).setCellValue("종료시간");
            header.createCell(6).setCellValue("비고");

            Row activity = sheet.createRow(1);
            activity.createCell(0).setCellValue("1일");
            activity.createCell(2).setCellValue("공항 도착");
            activity.createCell(3).setCellValue(9.0 / 24);
            activity.createCell(4).setCellValue(60.0 / (24 * 60));
            activity.createCell(5).setCellValue(10.0 / 24);

            Row movement = sheet.createRow(2);
            movement.createCell(2).setCellValue("공항->도쿄역");
            movement.createCell(3).setCellValue(11.0 / 24);
            movement.createCell(4).setCellValue(60.0 / (24 * 60));
            movement.createCell(5).setCellValue(12.0 / 24);

            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "고정시간.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        );

        PlanTransferDto result = service.preview(
                file,
                "고정 시간 여행",
                LocalDate.of(2025, 8, 4)
        );

        PlanTransferDto.Schedule station = result.days().get(0).schedules().get(1);
        assertThat(station.startTime().toString()).isEqualTo("12:00");
        assertThat(station.movingDuration()).isEqualTo(60);
        assertThat(station.fixedStartTime()).isTrue();
    }
}
