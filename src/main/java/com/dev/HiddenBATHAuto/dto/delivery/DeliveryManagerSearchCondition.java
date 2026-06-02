package com.dev.HiddenBATHAuto.dto.delivery;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeliveryManagerSearchCondition {

    private int page;
    private int size;

    private String searchType;
    private String keyword;

    private LocalDate fromDate;
    private LocalDate toDate;

    private String sortKey;
    private String sortDir;
}