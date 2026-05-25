package com.dev.HiddenBATHAuto.service.production;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPanelDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;

@Component
public class LowerCabinetV1CuttingRule implements MaterialCuttingRule {

    private static final Long LOWER_CABINET_CATEGORY_ID = 2L;

    @Override
    public String getRuleKey() {
        return "LOWER_CABINET_V1";
    }

    @Override
    public String getRuleName() {
        return "하부장 기본 재단 규칙 V1";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean supports(Order order, MaterialCuttingParsedOptionsDto parsedOptions) {
        if (order == null || order.getProductCategory() == null) {
            return false;
        }

        Long categoryId = order.getProductCategory().getId();
        String categoryName = order.getProductCategory().getName();

        return Objects.equals(categoryId, LOWER_CABINET_CATEGORY_ID) || "하부장".equals(categoryName);
    }

    @Override
    public List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto parsedOptions) {
        List<MaterialCuttingPanelDto> panels = new ArrayList<>();

        if (parsedOptions == null
                || parsedOptions.widthMm() == null
                || parsedOptions.depthMm() == null
                || parsedOptions.heightMm() == null) {
            return panels;
        }

        int requestW = positive(parsedOptions.widthMm());
        int requestD = positive(parsedOptions.depthMm());
        int requestH = positive(parsedOptions.heightMm());

        int t = positive(parsedOptions.materialThicknessMm());
        int legHeight = "LEG".equals(parsedOptions.bodyType()) ? positive(parsedOptions.legHeightMm()) : 0;

        int topBottomW = requestW
                - (parsedOptions.edgeLeft() ? t : 0)
                - (parsedOptions.edgeRight() ? t : 0);

        int topBottomD = requestD
                - (parsedOptions.edgeFront() ? t : 0)
                - (parsedOptions.edgeBack() ? t : 0);

        int bodyH = requestH - legHeight;

        /*
         * V1 기준
         *
         * 1) 위/아래판
         *    - 마구리 좌/우가 있으면 W에서 각각 T 차감
         *    - 마구리 전/후가 있으면 D에서 각각 T 차감
         *
         * 2) 좌/우판
         *    - 아웃도어: 문 두께만큼 D에서 추가 차감
         *    - 인도어: 옆판 D는 그대로 두고 문 폭에서 좌/우 T 차감
         *
         * 3) 전면
         *    - 인도어: 문 폭 = 내부 폭 기준이므로 좌/우 T 차감
         *    - 아웃도어: 문 폭 = 하부장 정면 기준 폭
         */
        int sideD = "OUTDOOR".equals(parsedOptions.doorMode())
                ? topBottomD - t
                : topBottomD;

        int frontW = "INDOOR".equals(parsedOptions.doorMode())
                ? topBottomW - (t * 2)
                : topBottomW;

        int backW = topBottomW;

        panels.add(panel("TOP", "위판", topBottomW, topBottomD, 1, "W", "D",
                "마구리 차감 후 상판 재단 규격"));

        panels.add(panel("BOTTOM", "아래판", topBottomW, topBottomD, 1, "W", "D",
                "마구리 차감 후 하판 재단 규격"));

        panels.add(panel("LEFT", "좌측판", sideD, bodyH, 1, "D", "H",
                "아웃도어일 경우 문 두께만큼 D 추가 차감"));

        panels.add(panel("RIGHT", "우측판", sideD, bodyH, 1, "D", "H",
                "아웃도어일 경우 문 두께만큼 D 추가 차감"));

        panels.add(panel("FRONT", "전면/문", frontW, bodyH, 1, "W", "H",
                "인도어일 경우 좌우 두께 차감"));

        panels.add(panel("BACK", "후면", backW, bodyH, 1, "W", "H",
                "기본 후면판 재단 규격"));

        return panels;
    }

    private MaterialCuttingPanelDto panel(
            String faceCode,
            String faceLabel,
            int widthMm,
            int heightMm,
            int quantity,
            String widthLabel,
            String heightLabel,
            String note
    ) {
        return new MaterialCuttingPanelDto(
                faceCode,
                faceLabel,
                Math.max(0, widthMm),
                Math.max(0, heightMm),
                quantity,
                widthLabel,
                heightLabel,
                note
        );
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}