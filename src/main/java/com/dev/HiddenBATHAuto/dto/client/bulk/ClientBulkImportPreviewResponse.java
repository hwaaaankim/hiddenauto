package com.dev.HiddenBATHAuto.dto.client.bulk;

import java.util.List;

public record ClientBulkImportPreviewResponse(
        boolean success,
        String message,
        int totalCount,
        int saveableCount,
        List<ClientBulkImportPreviewRow> rows
) {
}
