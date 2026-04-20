package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOrderAddSaveResponse {
    private boolean success;
    private String message;
    private Long taskId;
}