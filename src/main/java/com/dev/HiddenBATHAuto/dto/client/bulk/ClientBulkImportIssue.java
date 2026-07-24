package com.dev.HiddenBATHAuto.dto.client.bulk;

public record ClientBulkImportIssue(
        String level,
        String code,
        String message,
        Integer excelRowNumber
) {

    public static ClientBulkImportIssue error(String code, String message, Integer excelRowNumber) {
        return new ClientBulkImportIssue("ERROR", code, message, excelRowNumber);
    }

    public static ClientBulkImportIssue warning(String code, String message, Integer excelRowNumber) {
        return new ClientBulkImportIssue("WARNING", code, message, excelRowNumber);
    }
}
