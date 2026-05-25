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
            Map<String, String> sourceOptions
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