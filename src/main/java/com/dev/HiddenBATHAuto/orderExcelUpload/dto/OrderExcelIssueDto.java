package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderExcelIssueDto {
    private String level; // ERROR / WARN
    private Integer excelRowNumber;
    private Integer groupNo;
    private String field;
    private String message;

    public static OrderExcelIssueDto error(Integer excelRowNumber, Integer groupNo, String field, String message) {
        return new OrderExcelIssueDto("ERROR", excelRowNumber, groupNo, field, message);
    }

    public static OrderExcelIssueDto warn(Integer excelRowNumber, Integer groupNo, String field, String message) {
        return new OrderExcelIssueDto("WARN", excelRowNumber, groupNo, field, message);
    }
}
