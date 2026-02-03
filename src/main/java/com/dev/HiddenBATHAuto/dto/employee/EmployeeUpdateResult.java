package com.dev.HiddenBATHAuto.dto.employee;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmployeeUpdateResult {
    private boolean regionsCleared;
    private String message;
}