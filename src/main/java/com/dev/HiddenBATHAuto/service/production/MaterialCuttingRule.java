package com.dev.HiddenBATHAuto.service.production;

import java.util.List;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPanelDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;

public interface MaterialCuttingRule {

    String getRuleKey();

    String getRuleName();

    int getPriority();

    boolean supports(Order order, MaterialCuttingParsedOptionsDto parsedOptions);

    List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto parsedOptions);
}