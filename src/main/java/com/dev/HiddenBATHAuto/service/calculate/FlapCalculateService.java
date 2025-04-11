package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class FlapCalculateService {

	public Map<String, Object> calculate(Map<String, Object> selection) {
        int mainPrice = 0;
        int variablePrice = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 제품 ID 및 중분류 (시리즈)
        int productId = parseInt(selection.get("product"));
        int middleSort = parseInt(selection.get("middleSort"));
        reasons.add("시리즈(middleSort): " + middleSort);
        reasons.add("제품 ID: " + productId);

        // 2. 사이즈 파싱
        int width = 0, height = 0, depth = 0;
        String sizeStr = (String) selection.get("size");
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
                reasons.add("입력 사이즈: " + width + "x" + height + "x" + depth);
                mainPrice += 100000; // 임시 기본 가격
                reasons.add("사이즈 기반 기본 가격 적용됨");
            } catch (Exception e) {
                reasons.add("사이즈 파싱 실패");
            }
        }

        // 3. door 추가 여부
        String door = (String) selection.get("door");
        if ("add".equals(door)) {
            String formofdoor = (String) selection.get("formofdoor_other");
            if ("drawer".equals(formofdoor) || "mixed".equals(formofdoor)) {
                reasons.add("문의 형태에 따른 메시지 추가 (drawer 또는 mixed)");
            } else {
                reasons.add("문 추가됨 - 특이사항 없음");
            }
        } else {
            reasons.add("문 추가 안됨");
        }

        // 4. handle
        if ("add".equals(selection.get("handle"))) {
            reasons.add("손잡이 추가됨 (추가 메시지용)");
        } else {
            reasons.add("손잡이 추가 안됨");
        }

        // 5. LED
        if ("add".equals(selection.get("led"))) {
            String ledPosition = String.valueOf(selection.get("ledPosition"));
            int ledCount = ("5".equals(ledPosition)) ? 2 : 1;
            variablePrice += ledCount * 15000;
            reasons.add("LED " + ledCount + "개 추가됨, 임시가격 적용됨");
        } else {
            reasons.add("LED 없음");
        }

        // 6. outletPosition
        variablePrice += addIfOptionPresent(selection, "outletPosition", reasons);

        // 7. dryPosition
        variablePrice += addIfOptionPresent(selection, "dryPosition", reasons);

        // 8. tissuePosition
        variablePrice += addIfOptionPresent(selection, "tissuePosition", reasons);

        // 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", mainPrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);
        return result;
    }

    private int addIfOptionPresent(Map<String, Object> selection, String key, List<String> reasons) {
        Object value = selection.get(key);
        if (value == null) {
            reasons.add(key + " 정보 없음");
            return 0;
        }
        if (!"7".equals(String.valueOf(value))) {
            reasons.add(key + " 옵션 선택됨 (임시 비용 추가)");
            return 10000;
        } else {
            reasons.add(key + " 옵션 선택 안됨");
            return 0;
        }
    }

    private int parseInt(Object obj) {
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }
}