package com.dev.HiddenBATHAuto.dto.production;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductionOverviewRequest {

    private List<Long> orderIds = new ArrayList<>();
}