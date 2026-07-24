package com.dev.HiddenBATHAuto.dto.client.bulk;

import java.util.List;

public record ClientBulkImportSaveResponse(
        boolean success,
        String message,
        int savedCompanyCount,
        int savedMemberCount,
        List<ClientBulkImportIssue> issues
) {

    public static ClientBulkImportSaveResponse success(int companyCount, int memberCount) {
        return new ClientBulkImportSaveResponse(
                true,
                "대리점과 대표 멤버가 정상적으로 등록되었습니다.",
                companyCount,
                memberCount,
                List.of()
        );
    }

    public static ClientBulkImportSaveResponse failure(String message, List<ClientBulkImportIssue> issues) {
        return new ClientBulkImportSaveResponse(false, message, 0, 0, issues == null ? List.of() : issues);
    }
}
