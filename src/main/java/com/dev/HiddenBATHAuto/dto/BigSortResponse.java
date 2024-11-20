package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import lombok.Data;

@Data
public class BigSortResponse {
    private Long bigSortId;
    private String bigSortName;
    private List<MiddleSortResponse> middleSorts;
}
