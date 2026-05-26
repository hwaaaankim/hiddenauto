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

        boolean indoor = "INDOOR".equals(parsedOptions.doorMode());
        boolean outdoor = "OUTDOOR".equals(parsedOptions.doorMode());

        /*
         * 위/아래판 W 계산
         * requestW - 좌측마구리T - 우측마구리T
         */
        List<Deduction> topBottomWidthDeductions = new ArrayList<>();
        addDeduction(topBottomWidthDeductions, parsedOptions.edgeLeft(), t, "좌측마구리");
        addDeduction(topBottomWidthDeductions, parsedOptions.edgeRight(), t, "우측마구리");

        int topBottomW = applyDeductions(requestW, topBottomWidthDeductions);

        /*
         * 위/아래판 D 계산
         * requestD - 전면마구리T - 후면마구리T
         */
        List<Deduction> topBottomDepthDeductions = new ArrayList<>();
        addDeduction(topBottomDepthDeductions, parsedOptions.edgeFront(), t, "전면마구리");
        addDeduction(topBottomDepthDeductions, parsedOptions.edgeBack(), t, "후면마구리");

        int topBottomD = applyDeductions(requestD, topBottomDepthDeductions);

        /*
         * 본체 H 계산
         * 다리형이면 requestH - legHeight
         */
        List<Deduction> bodyHeightDeductions = new ArrayList<>();
        addDeduction(bodyHeightDeductions, legHeight > 0, legHeight, "다리높이");

        int bodyH = applyDeductions(requestH, bodyHeightDeductions);

        /*
         * 좌/우판 D 계산
         * 아웃도어: topBottomD - t
         * 인도어: topBottomD
         */
        List<Deduction> sideDepthDeductions = new ArrayList<>();
        addDeduction(sideDepthDeductions, parsedOptions.edgeFront(), t, "전면마구리");
        addDeduction(sideDepthDeductions, parsedOptions.edgeBack(), t, "후면마구리");
        addDeduction(sideDepthDeductions, outdoor, t, "아웃도어 문두께");

        int sideD = applyDeductions(requestD, sideDepthDeductions);

        /*
         * 전면/문 W 계산
         * 인도어: topBottomW - (t * 2)
         * 아웃도어: topBottomW
         */
        List<Deduction> frontWidthDeductions = new ArrayList<>();
        addDeduction(frontWidthDeductions, parsedOptions.edgeLeft(), t, "좌측마구리");
        addDeduction(frontWidthDeductions, parsedOptions.edgeRight(), t, "우측마구리");
        addDeduction(frontWidthDeductions, indoor, t, "인도어 좌측 내부폭");
        addDeduction(frontWidthDeductions, indoor, t, "인도어 우측 내부폭");

        int frontW = applyDeductions(requestW, frontWidthDeductions);

        /*
         * 후면 W 계산
         * backW = topBottomW
         */
        List<Deduction> backWidthDeductions = new ArrayList<>();
        addDeduction(backWidthDeductions, parsedOptions.edgeLeft(), t, "좌측마구리");
        addDeduction(backWidthDeductions, parsedOptions.edgeRight(), t, "우측마구리");

        int backW = applyDeductions(requestW, backWidthDeductions);

        String topBottomNote = formula("넓이(W)", requestW, topBottomW, topBottomWidthDeductions)
                + " / "
                + formula("깊이(D)", requestD, topBottomD, topBottomDepthDeductions);

        String sideNote = formula("깊이(D)", requestD, sideD, sideDepthDeductions)
                + " / "
                + formula("높이(H)", requestH, bodyH, bodyHeightDeductions);

        String frontNote = formula("넓이(W)", requestW, frontW, frontWidthDeductions)
                + " / "
                + formula("높이(H)", requestH, bodyH, bodyHeightDeductions);

        String backNote = formula("넓이(W)", requestW, backW, backWidthDeductions)
                + " / "
                + formula("높이(H)", requestH, bodyH, bodyHeightDeductions);

        panels.add(panel(
                "TOP",
                "위판",
                topBottomW,
                topBottomD,
                1,
                "W",
                "D",
                topBottomNote
        ));

        panels.add(panel(
                "BOTTOM",
                "아래판",
                topBottomW,
                topBottomD,
                1,
                "W",
                "D",
                topBottomNote
        ));

        panels.add(panel(
                "LEFT",
                "좌측판",
                sideD,
                bodyH,
                1,
                "D",
                "H",
                sideNote
        ));

        panels.add(panel(
                "RIGHT",
                "우측판",
                sideD,
                bodyH,
                1,
                "D",
                "H",
                sideNote
        ));

        panels.add(panel(
                "FRONT",
                "전면/문",
                frontW,
                bodyH,
                1,
                "W",
                "H",
                frontNote
        ));

        panels.add(panel(
                "BACK",
                "후면",
                backW,
                bodyH,
                1,
                "W",
                "H",
                backNote
        ));

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

    private int applyDeductions(int baseMm, List<Deduction> deductions) {
        int result = baseMm;

        if (deductions != null) {
            for (Deduction deduction : deductions) {
                if (deduction == null || deduction.amountMm() <= 0) {
                    continue;
                }

                result -= deduction.amountMm();
            }
        }

        return Math.max(0, result);
    }

    private void addDeduction(List<Deduction> deductions, boolean condition, int amountMm, String reason) {
        if (!condition || deductions == null || amountMm <= 0) {
            return;
        }

        deductions.add(new Deduction(amountMm, reason));
    }

    private String formula(String label, int baseMm, int resultMm, List<Deduction> deductions) {
        StringBuilder builder = new StringBuilder();

        builder.append(label)
                .append(": ")
                .append(baseMm);

        if (deductions != null) {
            for (Deduction deduction : deductions) {
                if (deduction == null || deduction.amountMm() <= 0) {
                    continue;
                }

                builder.append(" - ")
                        .append(deduction.amountMm())
                        .append("(")
                        .append(deduction.reason())
                        .append(")");
            }
        }

        builder.append(" = ")
                .append(resultMm)
                .append(" mm");

        return builder.toString();
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private record Deduction(
            int amountMm,
            String reason
    ) {
    }
}