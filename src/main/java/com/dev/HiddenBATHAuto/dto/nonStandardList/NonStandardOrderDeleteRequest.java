package com.dev.HiddenBATHAuto.dto.nonStandardList;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NonStandardOrderDeleteRequest {
    private List<Long> orderIds = new ArrayList<>();
}