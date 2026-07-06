package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelPreviewResponse {
    private boolean success;
    private String message;
    private List<OrderExcelPreviewGroupDto> groups = new ArrayList<>();
    private List<OrderExcelIssueDto> issues = new ArrayList<>();
    private OrderExcelLookupOptionsResponse options = new OrderExcelLookupOptionsResponse();

    public boolean hasErrors() {
        if (issues != null && issues.stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.getLevel()))) {
            return true;
        }

        if (groups == null) {
            return false;
        }

        return groups.stream().anyMatch(group ->
                (group.getIssues() != null && group.getIssues().stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.getLevel())))
                        || (group.getRows() != null && group.getRows().stream().anyMatch(row ->
                        row.getIssues() != null && row.getIssues().stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.getLevel()))
                ))
        );
    }
}
