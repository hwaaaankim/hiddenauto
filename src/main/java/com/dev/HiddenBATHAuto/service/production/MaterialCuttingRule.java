package com.dev.HiddenBATHAuto.service.production;

import java.util.List;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPanelDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;

public interface MaterialCuttingRule {

    String getRuleKey();

    /**
     * 기존 LowerCabinetV1CuttingRule 호환용입니다.
     * 구현체에서 별도 이름을 제공하지 않으면 ruleKey를 표시명으로 사용합니다.
     */
    default String getRuleName() {
        return getRuleKey();
    }

    /**
     * 여러 공식이 동시에 supports=true일 때 우선순위가 높은 공식부터 적용합니다.
     * 기존 LowerCabinetV1CuttingRule의 priority=100과 호환됩니다.
     */
    default int getPriority() {
        return 0;
    }

    boolean supports(Order order, MaterialCuttingParsedOptionsDto options);

    /**
     * 기존 구현체 호환용 기본 시그니처입니다.
     */
    List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto options);

    /**
     * 신규 재단표에서는 주문 수량을 panel.quantity에 반영할 수 있습니다.
     */
    default List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto options, int orderQuantity) {
        return calculate(options);
    }
}
