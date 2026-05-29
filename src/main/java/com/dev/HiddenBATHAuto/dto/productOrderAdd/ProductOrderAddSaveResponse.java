package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderAddSaveResponse {

    private boolean success;
    private String message;
    private Long taskId;
}
