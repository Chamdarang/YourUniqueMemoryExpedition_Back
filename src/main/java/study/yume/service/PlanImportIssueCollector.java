package study.yume.service;

import study.yume.dto.plan.response.PlanImportPreviewResponse;

import java.util.ArrayList;
import java.util.List;

final class PlanImportIssueCollector {
    private final List<PlanImportPreviewResponse.Issue> issues = new ArrayList<>();

    void addRowIssue(int zeroBasedRow, String severity, String message, String value) {
        issues.add(new PlanImportPreviewResponse.Issue(
                zeroBasedRow + 1,
                severity,
                message,
                truncate(value, 200)
        ));
    }

    void add(PlanImportPreviewResponse.Issue issue) {
        issues.add(issue);
    }

    List<PlanImportPreviewResponse.Issue> all() {
        return List.copyOf(issues);
    }

    int skippedRows() {
        return (int) issues.stream().filter(issue -> "ERROR".equals(issue.severity())).count();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }
}
