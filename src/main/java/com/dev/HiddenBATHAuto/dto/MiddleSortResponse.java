package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import lombok.Data;

@Data
public class MiddleSortResponse {
    private Long middleSortId;
    private String middleSortName;
    private List<ProductResponse> products;
}