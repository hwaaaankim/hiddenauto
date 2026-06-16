package com.dev.HiddenBATHAuto.dto.amount;

import java.util.List;
import java.util.Map;

public record AmountGridResponse(
        List<AmountExcelColumnDto> columns,
        List<Map<String, Object>> rows,
        long total,
        int offset,
        int limit,
        boolean hasMore
) {
}
