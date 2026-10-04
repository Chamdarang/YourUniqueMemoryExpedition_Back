package study.yume.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.plan.response.PlanImportAnalysisResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class PlanImportTableReader {

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_SHEETS = 20;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_PREVIEW_SAMPLES = 5;
    private static final Set<String> KNOWN_HEADERS = Set.of(
            "일자", "날짜", "장소", "장소명", "목적", "시작", "시작시간",
            "소요시간", "체류시간", "종료", "종료시간", "교통", "교통수단", "비고", "메모"
    );

    TableFile read(MultipartFile file, String charsetName, String delimiterValue) {
        validateFile(file);
        String extension = extension(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            if ("csv".equals(extension)) {
                String detectedCharset = resolveCharset(bytes, charsetName);
                char delimiter = resolveDelimiter(bytes, detectedCharset, delimiterValue);
                return new TableFile(
                        "CSV",
                        detectedCharset,
                        Character.toString(delimiter),
                        List.of(readCsv(bytes, detectedCharset, delimiter, file.getOriginalFilename()))
                );
            }
            if (!Set.of("xlsx", "xls").contains(extension)) {
                throw new IllegalArgumentException("XLSX, XLS, CSV 파일만 지원합니다.");
            }
            return new TableFile(
                    extension.toUpperCase(Locale.ROOT),
                    "",
                    "",
                    readWorkbook(bytes)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.error("general_import_read_error filename={} size={}",
                    file.getOriginalFilename(), file.getSize(), exception);
            throw new IllegalArgumentException("파일을 읽지 못했습니다. 형식과 인코딩을 확인해 주세요.", exception);
        }
    }

    PlanImportAnalysisResponse.Sheet analyzeSheet(TableSheet sheet) {
        int headerIndex = suggestHeaderRow(sheet.rows());
        int maxColumns = sheet.rows().stream().mapToInt(List::size).max().orElse(0);
        List<PlanImportAnalysisResponse.Column> columns = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < maxColumns; columnIndex++) {
            String header = value(sheet.rows(), headerIndex, columnIndex);
            String label = header.isBlank() ? columnLabel(columnIndex) : header;
            List<String> samples = new ArrayList<>();
            for (int rowIndex = headerIndex + 1;
                 rowIndex < sheet.rows().size() && samples.size() < MAX_PREVIEW_SAMPLES;
                 rowIndex++) {
                String sample = value(sheet.rows(), rowIndex, columnIndex);
                if (!sample.isBlank()) samples.add(sample);
            }
            columns.add(new PlanImportAnalysisResponse.Column(columnIndex, label, samples));
        }
        return new PlanImportAnalysisResponse.Sheet(
                sheet.name(),
                sheet.rows().size(),
                headerIndex + 1,
                columns
        );
    }

    private List<TableSheet> readWorkbook(byte[] bytes) throws IOException {
        List<TableSheet> sheets = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        int totalRows = 0;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                throw new IllegalArgumentException("시트는 최대 " + MAX_SHEETS + "개까지 가져올 수 있습니다.");
            }
            for (Sheet sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<String> values = new ArrayList<>();
                    if (row != null) {
                        for (int column = 0; column < Math.max(0, row.getLastCellNum()); column++) {
                            values.add(formatCell(row.getCell(column), formatter));
                        }
                    }
                    rows.add(trimTrailingEmpty(values));
                    totalRows++;
                    if (totalRows > MAX_ROWS) {
                        throw new IllegalArgumentException("데이터 행은 최대 " + MAX_ROWS + "개까지 가져올 수 있습니다.");
                    }
                }
                sheets.add(new TableSheet(sheet.getSheetName(), rows));
            }
        }
        return sheets;
    }

    private String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell).trim();
        }
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> formatter.formatRawCellContents(
                    cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(),
                    cell.getCellStyle().getDataFormatString()
            ).trim();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private TableSheet readCsv(byte[] bytes, String charsetName, char delimiter, String fileName) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .get();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), Charset.forName(charsetName));
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new IllegalArgumentException("데이터 행은 최대 " + MAX_ROWS + "개까지 가져올 수 있습니다.");
                }
                List<String> values = new ArrayList<>();
                record.forEach(value -> values.add(value == null ? "" : value.trim()));
                if (rows.isEmpty() && !values.isEmpty() && values.get(0).startsWith("\uFEFF")) {
                    values.set(0, values.get(0).substring(1));
                }
                rows.add(trimTrailingEmpty(values));
            }
        }
        return new TableSheet(safeFileName(fileName), rows);
    }

    private String resolveCharset(byte[] bytes, String requested) {
        if (requested != null && !requested.isBlank() && !"AUTO".equalsIgnoreCase(requested)) {
            return Charset.forName(requested).name();
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8.name();
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8.name();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("MS949").name();
        }
    }

    private char resolveDelimiter(byte[] bytes, String charsetName, String requested) {
        if (requested != null && !requested.isBlank() && !"AUTO".equalsIgnoreCase(requested)) {
            return switch (requested.toUpperCase(Locale.ROOT)) {
                case "TAB", "\\T", "\\t" -> '\t';
                case "SEMICOLON" -> ';';
                default -> requested.charAt(0);
            };
        }
        String text = new String(bytes, Charset.forName(charsetName));
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (char candidate : List.of(',', '\t', ';')) {
            counts.put(candidate, countOutsideQuotes(firstLine, candidate));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    private int countOutsideQuotes(String line, char target) {
        int count = 0;
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') quoted = !quoted;
            else if (!quoted && current == target) count++;
        }
        return count;
    }

    private int suggestHeaderRow(List<List<String>> rows) {
        int bestIndex = 0;
        int bestScore = -1;
        for (int rowIndex = 0; rowIndex < Math.min(rows.size(), 30); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int nonBlank = (int) row.stream().filter(value -> !value.isBlank()).count();
            int known = (int) row.stream()
                    .map(String::trim)
                    .filter(KNOWN_HEADERS::contains)
                    .count();
            int score = nonBlank + known * 5;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = rowIndex;
            }
        }
        return bestIndex;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("가져올 파일이 필요합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일은 10MB 이하여야 합니다.");
        }
    }

    private String value(List<List<String>> rows, int row, int column) {
        return row < 0 || row >= rows.size() || column < 0 || column >= rows.get(row).size()
                ? ""
                : rows.get(row).get(column);
    }

    private String columnLabel(int index) {
        int value = index + 1;
        StringBuilder label = new StringBuilder();
        while (value > 0) {
            value--;
            label.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return label.toString();
    }

    private List<String> trimTrailingEmpty(List<String> values) {
        int last = values.size();
        while (last > 0 && values.get(last - 1).isBlank()) last--;
        return new ArrayList<>(values.subList(0, last));
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "가져온 파일";
        return fileName.replaceAll("[\\r\\n]", "").trim();
    }

    private String extension(String fileName) {
        String safe = safeFileName(fileName);
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    record TableFile(String fileType, String charset, String delimiter, List<TableSheet> sheets) {
    }

    record TableSheet(String name, List<List<String>> rows) {
    }
}
