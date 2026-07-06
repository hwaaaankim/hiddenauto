package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderExcelSaveResponse {
    private boolean success;
    private String message;
    private int taskCount;
    private int orderCount;
    private List<Long> taskIds = new ArrayList<>();
    private List<OrderExcelIssueDto> issues = new ArrayList<>();
}
