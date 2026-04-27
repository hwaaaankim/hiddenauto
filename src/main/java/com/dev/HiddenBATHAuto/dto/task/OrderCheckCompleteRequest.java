package com.dev.HiddenBATHAuto.dto.task;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCheckCompleteRequest {
    private List<Long> orderIds = new ArrayList<>();
}