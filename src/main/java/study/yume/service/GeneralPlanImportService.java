package study.yume.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import study.yume.dto.plan.request.GeneralImportConfig;
import study.yume.dto.plan.response.PlanImportAnalysisResponse;
import study.yume.dto.plan.response.PlanImportPreviewResponse;

@Service
public class GeneralPlanImportService {

    private final PlanImportTableReader tableReader;
    private final GeneralPlanImportPreviewBuilder previewBuilder;

    public GeneralPlanImportService(
            PlanImportTableReader tableReader,
            GeneralPlanImportPreviewBuilder previewBuilder
    ) {
        this.tableReader = tableReader;
        this.previewBuilder = previewBuilder;
    }

    public PlanImportAnalysisResponse analyze(MultipartFile file, String charset, String delimiter) {
        PlanImportTableReader.TableFile table = tableReader.read(file, charset, delimiter);
        return new PlanImportAnalysisResponse(
                file.getOriginalFilename(),
                table.fileType(),
                table.charset(),
                table.delimiter(),
                table.sheets().stream().map(tableReader::analyzeSheet).toList()
        );
    }

    public PlanImportPreviewResponse preview(MultipartFile file, GeneralImportConfig config) {
        return previewBuilder.preview(file, config);
    }
}