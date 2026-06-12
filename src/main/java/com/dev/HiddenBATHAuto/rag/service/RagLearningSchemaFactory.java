package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RagLearningSchemaFactory {
    private RagLearningSchemaFactory() {}

    public static Map<String, Object> emptyProcess(String projectTitle, String learningDirection) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaType", "ORDER_PROCESS_BUILDER_V2");
        root.put("projectTitle", projectTitle);
        root.put("learningPhase", "PROCESS_AND_PRICING");
        root.put("learningDirection", learningDirection == null ? "" : learningDirection);
        root.put("steps", new ArrayList<>());
        root.put("stepSchema", Map.of(
                "stepKey", "영문/숫자/언더바 기반 고유 키. 예: series, item, install_type, size, color",
                "orderNo", "고객에게 물어볼 순서",
                "title", "관리자용 스텝명",
                "question", "고객에게 실제로 보여줄 질문",
                "answerType", "SELECT|MULTI_SELECT|NUMBER|SIZE_WDH|TEXT|FILE_IMAGE|BOOLEAN|COORDINATE_2D|OPTION_GROUP",
                "required", true,
                "options", "SELECT/MULTI_SELECT일 때 선택지 배열",
                "showCondition", "특정 시리즈/형태/품목에서만 보일 조건 JSON",
                "validation", "최소/최대/허용값/AS 가능 여부 등 검증 규칙 JSON",
                "pricingBindings", "이 답변이 어떤 가격계산 rule 또는 matrix에 연결되는지"
        ));
        root.put("stepOrderPolicy", "steps.orderNo ASC. showCondition이 false면 해당 스텝은 건너뜁니다. nextRules가 있으면 nextRules 우선입니다.");
        root.put("adminNotes", new ArrayList<>());
        root.put("requiredRuntimeGuarantees", List.of(
                "고객에게 묻는 질문은 steps.question만 기준으로 생성합니다.",
                "가격 산술 계산은 pricingJson.calculationRules와 구조화 엑셀 테이블을 기준으로 서버에서 수행합니다.",
                "AI가 모르는 옵션/가격을 추정해서 고객에게 확정 금액처럼 말하면 안 됩니다."
        ));
        return root;
    }

    public static Map<String, Object> emptyPricing() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaType", "ORDER_PRICING_ENGINE_V2");
        root.put("currency", "KRW");
        root.put("structuredTableRoles", Map.of(
                "BASE_PRICE_TABLE", "제품명/제품코드/사이즈/색상/기준가격 같은 1차원 표",
                "BASE_PRICE_MATRIX", "W-D 또는 D-W 기준 기본금액 2차원 표",
                "COUNTERTOP_PRICE_MATRIX", "상판 등급별 W-D 2차원 표",
                "SIZE_CONSTRAINT_TABLE", "품목별 W/D/H 최소·최대 및 AS 가능 조건 표",
                "COLOR_RULE_TABLE", "품목별 가능 색상 표",
                "OPTION_PRICE_TABLE", "손잡이/타공/LED/휴지걸이/드라이걸이 등 옵션 단가 표"
        ));
        root.put("dimensionPolicy", Map.of(
                "width", "가격표 조회 시 100단위 올림",
                "depth", "가격표 조회 시 100단위 올림",
                "height", "높이 추가금 규칙이 있을 때 100단위 올림"
        ));
        root.put("basePriceRules", new ArrayList<>());
        root.put("optionPriceRules", new ArrayList<>());
        root.put("calculationRules", new ArrayList<>());
        root.put("excelTables", new ArrayList<>());
        root.put("calculationOrder", List.of(
                "BASE_PRICE_TABLE_OR_MATRIX",
                "HEIGHT_SURCHARGE_BY_100_CEIL",
                "COUNTERTOP_PRICE_MATRIX",
                "BASIN_OPTION",
                "DOOR_DRAWER_OPTION",
                "HANDLE_OPTION",
                "EDGE_BANDING_OPTION",
                "HOLE_OPTION",
                "ETC_OPTION",
                "TOTAL"
        ));
        root.put("supportedRuleTypes", List.of(
                "BASE_TABLE_LOOKUP",
                "MATRIX_LOOKUP",
                "HEIGHT_SURCHARGE_BY_100_CEIL",
                "FIXED_PER_COUNT",
                "FREE_COUNT_THEN_UNIT",
                "HANDLE_PRICE_BY_TYPE",
                "EDGE_BANDING_BY_SIDES"
        ));
        root.put("exampleCalculationRule", Map.of(
                "code", "HEIGHT_SURCHARGE",
                "label", "H 추가금",
                "type", "HEIGHT_SURCHARGE_BY_100_CEIL",
                "baseHeight", 600,
                "unit", 100,
                "amountPerUnit", 5000
        ));
        return root;
    }

    public static Map<String, Object> emptyConstraints() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaType", "ORDER_CONSTRAINTS_V2");
        root.put("rules", new ArrayList<>());
        root.put("skipRules", new ArrayList<>());
        root.put("answerFilterRules", new ArrayList<>());
        root.put("asPolicyRules", new ArrayList<>());
        root.put("validationRuleSchema", Map.of(
                "targetStepKey", "size 또는 color 등 검증 대상 stepKey",
                "condition", "이 규칙이 적용될 조건",
                "min", "최소값",
                "max", "최대값",
                "warningWhenExceeded", "초과 가능하지만 경고해야 하는 경우의 문구",
                "blockWhenExceeded", "초과 시 주문 불가라면 true"
        ));
        return root;
    }

    public static Map<String, Object> emptyValidation() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("completionScore", 0);
        root.put("readyToPublish", false);
        root.put("gaps", List.of("고객에게 물어볼 기본 스텝 순서와 가격계산 규칙이 필요합니다."));
        root.put("conflicts", new ArrayList<>());
        root.put("warnings", new ArrayList<>());
        root.put("assumptions", new ArrayList<>());
        root.put("requiredBeforePublish", List.of(
                "필수 steps가 모두 존재해야 합니다.",
                "기본금액 계산에 사용할 BASE_PRICE_TABLE 또는 BASE_PRICE_MATRIX가 있어야 합니다.",
                "각 옵션 추가금은 calculationRules 또는 OPTION_PRICE_TABLE 중 하나로 연결되어야 합니다.",
                "단가 교체 시 기존 active 자료가 비활성화되었는지 확인해야 합니다."
        ));
        root.put("lastQuestion", "먼저 고객에게 물어볼 스텝 순서와 각 스텝의 답변 형태를 알려주세요. 예: 시리즈: 선택, 품목: 선택, 사이즈: W/D/H 숫자 입력, 색상: 선택.");
        return root;
    }
}
