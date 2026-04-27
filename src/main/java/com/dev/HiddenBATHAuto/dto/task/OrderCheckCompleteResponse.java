package com.dev.HiddenBATHAuto.dto.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCheckCompleteResponse {
    private boolean success;
    private String message;
    private int checkedCount;
}