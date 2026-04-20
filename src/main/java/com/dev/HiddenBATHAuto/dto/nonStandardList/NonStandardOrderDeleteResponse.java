package com.dev.HiddenBATHAuto.dto.nonStandardList;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NonStandardOrderDeleteResponse {

    private boolean success;
    private String message;
    private int requestedOrderCount;
    private int deletedOrderCount;
    private int deletedTaskCount;

    public static NonStandardOrderDeleteResponse fail(String message) {
        return NonStandardOrderDeleteResponse.builder()
                .success(false)
                .message(message)
                .requestedOrderCount(0)
                .deletedOrderCount(0)
                .deletedTaskCount(0)
                .build();
    }
}