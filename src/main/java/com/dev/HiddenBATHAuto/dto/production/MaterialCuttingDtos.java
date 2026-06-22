package com.dev.HiddenBATHAuto.dto.production;

import java.util.List;
import java.util.Map;

public class MaterialCuttingDtos {

    private MaterialCuttingDtos() {
    }

    public record MaterialCuttingPageResponse(
            String ruleVersion,
            String generatedAtText,
            List<MaterialCuttingOrderDto> orders
    ) {
    }

    public record MaterialCuttingOrderDto(
            Long orderId,
            String companyName,
            String categoryName,
            String productSeries,
            String productName,
            String productOptionText,
            int quantity,
            String adminMemo,
            String orderComment,
            MaterialCuttingParsedOptionsDto parsedOptions,
            List<MaterialCuttingPanelDto> panels,
            List<String> warnings
    ) {
    }

    /**
     * 기존 필드는 최대한 유지하고, 재단 공식 선택에 필요한 필드를 뒤에 추가했습니다.
     * Thymeleaf에서 기존 필드명으로 접근하던 부분이 바로 깨지지 않도록 구성했습니다.
     */
    public record MaterialCuttingParsedOptionsDto(
            Integer widthMm,
            Integer depthMm,
            Integer heightMm,
            int materialThicknessMm,
            int legHeightMm,
            String bodyType,
            String bodyTypeLabel,
            String doorMode,
            String doorModeLabel,
            boolean edgeFront,
            boolean edgeBack,
            boolean edgeLeft,
            boolean edgeRight,
            String edgeLabel,
            Map<String, String> sourceOptions,

            String cuttingSeries,
            String cuttingSeriesLabel,
            String formulaCode,
            String formulaLabel,
            String installType,
            String installTypeLabel,
            String topType,
            String topTypeLabel,
            String marbleEdgeType,
            String marbleEdgeLabel,
            int doorCount,
            boolean indoorDoor,
            boolean sixHundredWidthTarget,
            Integer bodyWidthMm,
            Integer bodyDepthMm,
            Integer bodyHeightMm,
            String formulaSummary
    ) {
    }

    public record MaterialCuttingPanelDto(
            String faceCode,
            String faceLabel,
            int widthMm,
            int heightMm,
            int quantity,
            String widthLabel,
            String heightLabel,
            String note
    ) {
    }
}
