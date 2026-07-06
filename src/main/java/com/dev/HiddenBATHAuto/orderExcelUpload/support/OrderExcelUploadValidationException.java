package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelIssueDto;

import lombok.Getter;

@Getter
public class OrderExcelUploadValidationException extends RuntimeException {
    private final List<OrderExcelIssueDto> issues;

    public OrderExcelUploadValidationException(String message, List<OrderExcelIssueDto> issues) {
        super(message);
        this.issues = issues == null ? new ArrayList<>() : issues;
    }
}
