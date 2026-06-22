package com.dev.HiddenBATHAuto.service.production;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPanelDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.service.production.MaterialCuttingSeriesProfile.FrontBandType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HingedIndoor600CabinetCuttingRule implements MaterialCuttingRule {

    private static final int MATERIAL_T = 15;
    private static final int DOOR_T = 18;
    private static final int DOOR_GAP_MM = 2;
    private static final int BOTTOM_CLEARANCE_MM = 3;

    private final MaterialCuttingSeriesProfileRegistry seriesProfileRegistry;

    @Override
    public String getRuleKey() {
        return MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE;
    }

    @Override
    public String getRuleName() {
        return "6xx 인도어 여닫이 확장 재단 공식";
    }

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public boolean supports(Order order, MaterialCuttingParsedOptionsDto options) {
        if (options == null) {
            return false;
        }

        if (!MaterialCuttingFormulaCodes.HINGED_INDOOR_600_BASE.equals(options.formulaCode())) {
            return false;
        }

        if (seriesProfileRegistry.findByCode(options.cuttingSeries()).isEmpty()) {
            return false;
        }

        if (!options.sixHundredWidthTarget()) {
            return false;
        }

        if (!"HINGED".equals(options.doorMode())) {
            return false;
        }

        if (!options.indoorDoor()) {
            return false;
        }

        if (!"LEG".equals(options.installType()) && !"WALL".equals(options.installType())) {
            return false;
        }

        if (!"CERAMIC".equals(options.topType()) && !"MARBLE".equals(options.topType())) {
            return false;
        }

        if ("MARBLE".equals(options.topType()) && "UNKNOWN".equals(options.marbleEdgeType())) {
            return false;
        }

        return options.widthMm() != null
                && options.depthMm() != null
                && options.heightMm() != null
                && options.bodyWidthMm() != null
                && options.bodyDepthMm() != null
                && options.bodyHeightMm() != null;
    }

    @Override
    public List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto options) {
        return calculate(options, 1);
    }

    @Override
    public List<MaterialCuttingPanelDto> calculate(MaterialCuttingParsedOptionsDto options, int orderQuantity) {
        MaterialCuttingSeriesProfile profile = seriesProfileRegistry.findByCode(options.cuttingSeries())
                .orElseThrow(() -> new IllegalArgumentException("지원 시리즈 프로필이 없습니다: " + options.cuttingSeries()));

        int qtyMultiplier = Math.max(orderQuantity, 1);

        int bodyW = options.bodyWidthMm();
        int bodyD = options.bodyDepthMm();
        int bodyH = options.bodyHeightMm();

        int innerW = Math.max(bodyW - (MATERIAL_T * 2), 0);
        int bottomD = "CERAMIC".equals(options.topType())
                ? Math.max(bodyD - DOOR_T - BOTTOM_CLEARANCE_MM, 0)
                : bodyD;

        int backBandTopH = resolveBackBandTopHeight(options);
        int backBandBottomH = resolveBackBandBottomHeight(options);

        int doorCount = Math.max(options.doorCount(), 1);
        int doorW = Math.max((innerW - (DOOR_GAP_MM * (doorCount + 1))) / doorCount, 0);
        int doorH = Math.max(resolveDoorHeight(options, profile, bodyH), 0);

        List<MaterialCuttingPanelDto> panels = new ArrayList<>();

        panels.add(panel(
                "SIDE",
                "측판",
                bodyD,
                bodyH,
                2 * qtyMultiplier,
                "D",
                "H",
                "계산된 장 D/H 기준. 좌/우 동일 2EA."
        ));

        panels.add(panel(
                "BOTTOM_BOARD",
                "바닥판(지판)",
                innerW,
                bottomD,
                1 * qtyMultiplier,
                "W",
                "D",
                "측판 안쪽으로 들어가므로 W에서 15T×2 차감"
                        + ("CERAMIC".equals(options.topType()) ? ", 도어 18T와 공차 3mm 차감." : ".")
        ));

        panels.add(panel(
                "BACK_BAND_TOP",
                "위 뒷밴드",
                innerW,
                backBandTopH,
                1 * qtyMultiplier,
                "W",
                "H",
                buildBackBandNote(options, "위", backBandTopH)
        ));

        panels.add(panel(
                "BACK_BAND_BOTTOM",
                "아래 뒷밴드",
                innerW,
                backBandBottomH,
                1 * qtyMultiplier,
                "W",
                "H",
                buildBackBandNote(options, "아래", backBandBottomH)
        ));

        addFrontBandPanels(panels, profile, innerW, qtyMultiplier);

        panels.add(panel(
                "DOOR",
                "도어",
                doorW,
                doorH,
                doorCount * qtyMultiplier,
                "W",
                "H",
                "인도어 기준. W=(바닥판W - 2mm×문틈 " + (doorCount + 1) + "곳)/" + doorCount
                        + ", H=장H - 시리즈별 손잡이공간 + 시리즈별 보정값."
        ));

        if ("LEG".equals(options.installType())) {
            panels.add(panel(
                    "KICK_PLATE",
                    "걸레받이",
                    innerW,
                    30,
                    1 * qtyMultiplier,
                    "W",
                    "H",
                    "다리형만 적용. 전면 띠 형태, H 30mm 고정."
            ));

            panels.add(panel(
                    "LEG_ACCESSORY",
                    "다리",
                    0,
                    0,
                    1 * qtyMultiplier,
                    "-",
                    "-",
                    "다리 부속 필요. 재단치수는 별도 자재이므로 도면 없이 수량만 표시."
            ));
        }

        return panels;
    }

    private void addFrontBandPanels(
            List<MaterialCuttingPanelDto> panels,
            MaterialCuttingSeriesProfile profile,
            int innerW,
            int qtyMultiplier
    ) {
        FrontBandType type = profile.frontBandType();

        if (type == FrontBandType.SOFT_SPLIT_25_95) {
            panels.add(panel(
                    "FRONT_L_LOWER",
                    "앞밴드 L 하부",
                    innerW,
                    25,
                    1 * qtyMultiplier,
                    "W",
                    "H",
                    profile.seriesLabel() + ": 기존 L자판 대신 2개 자재로 L 형태 구성. 하부 25mm."
            ));
            panels.add(panel(
                    "FRONT_L_UPPER",
                    "앞밴드 L 상부",
                    innerW,
                    95,
                    1 * qtyMultiplier,
                    "W",
                    "H",
                    profile.seriesLabel() + ": 기존 L자판 대신 2개 자재로 L 형태 구성. 상부 95mm."
            ));
            return;
        }

        panels.add(panel(
                "FRONT_L_BOARD",
                "앞밴드(L자판)",
                innerW,
                70,
                1 * qtyMultiplier,
                "W",
                "H",
                "바닥판 W와 동일, H 70mm 고정."
        ));

        if (type == FrontBandType.SINGLE_L_WITH_EXTRA_70) {
            panels.add(panel(
                    "FRONT_EXTRA_BAND",
                    "앞밴드 추가띠",
                    innerW,
                    70,
                    1 * qtyMultiplier,
                    "W",
                    "H",
                    profile.seriesLabel() + "만 추가. L자판 외 전면 띠 70mm."
            ));
        }
    }

    private int resolveBackBandTopHeight(MaterialCuttingParsedOptionsDto options) {
        if ("WALL".equals(options.installType())) {
            return 70;
        }

        return 145;
    }

    private int resolveBackBandBottomHeight(MaterialCuttingParsedOptionsDto options) {
        if ("WALL".equals(options.installType())) {
            return 70;
        }

        if ("MARBLE".equals(options.topType())) {
            return 70;
        }

        return 145;
    }

    private int resolveDoorHeight(
            MaterialCuttingParsedOptionsDto options,
            MaterialCuttingSeriesProfile profile,
            int bodyH
    ) {
        int gap = "MARBLE".equals(options.topType())
                ? profile.marbleDoorHandleGapMm()
                : profile.ceramicDoorHandleGapMm();

        return bodyH - gap + profile.doorHeightAddMm();
    }

    private String buildBackBandNote(MaterialCuttingParsedOptionsDto options, String positionLabel, int bandH) {
        return positionLabel + " 뒷밴드. "
                + options.topTypeLabel() + "/" + options.installTypeLabel()
                + " 기준 H " + bandH + "mm 적용.";
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
                widthMm,
                heightMm,
                quantity,
                widthLabel,
                heightLabel,
                note
        );
    }
}
