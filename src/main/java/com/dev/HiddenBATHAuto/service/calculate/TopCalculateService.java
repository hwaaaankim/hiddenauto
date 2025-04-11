package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class TopCalculateService {

	public Map<String, Object> calculate(Map<String, Object> selection) {
        int mainPrice = 0;
        int variablePrice = 0;
        List<String> reasons = new ArrayList<>();

        // 중분류 ID 및 제품 ID 파싱
        int middleSort = parseInt(selection.get("middleSort"));
        int productId = parseInt(selection.get("product"));
        reasons.add("선택된 제품 ID: " + productId);

        // 제품 정보 조회 (임시)
        String productName = getProductNameById(productId);
        reasons.add("제품명: " + productName);

        // 사이즈 파싱
        String sizeStr = (String) selection.get("size");
        int width = 0, height = 0, depth = 0;
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
                mainPrice += 100000; // 임시
                reasons.add("사이즈 입력됨: " + width + "/" + height + "/" + depth);
            } catch (Exception e) {
                reasons.add("사이즈 파싱 실패");
            }
        }

        // door 관련 메시지
        String door = (String) selection.get("door");
        if ("add".equals(door)) {
            String formofdoor = (String) selection.get("formofdoor_other");
            if ("drawer".equals(formofdoor) || "mixed".equals(formofdoor)) {
                reasons.add("문 옵션: drawer/mixed 에 따라 메시지 추가됨");
            } else {
                reasons.add("문 옵션: 처리되지 않음");
            }
        } else {
            reasons.add("문 추가 안함 (not_add)");
        }

        // 손잡이
        if ("add".equals(selection.get("handle"))) {
            reasons.add("손잡이 옵션 선택됨 (임시 메시지)");
        } else {
            reasons.add("손잡이 없음");
        }

        // LED
        if ("add".equals(selection.get("led"))) {
            String pos = String.valueOf(selection.get("ledPosition"));
            int ledCount = ("5".equals(pos)) ? 2 : 1;
            variablePrice += ledCount * 15000;
            reasons.add("LED " + ledCount + "개 추가됨");
        } else {
            reasons.add("LED 없음");
        }

        // outlet / dry / tissue 처리
        variablePrice += addIfOptionPresent(selection, "outletPosition", reasons);
        variablePrice += addIfOptionPresent(selection, "dryPosition", reasons);
        variablePrice += addIfOptionPresent(selection, "tissuePosition", reasons);

        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", mainPrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);

        return result;
    }

    private int parseInt(Object obj) {
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }

    private String getProductNameById(int productId) {
        // TODO: DB 연동 시 Repository 통해 실제 제품명 조회
        return productId == 123 ? "TOP 시리즈 제품" : "상부장 기본 제품";
    }

    private int addIfOptionPresent(Map<String, Object> selection, String key, List<String> reasons) {
        Object value = selection.get(key);
        if (value == null) {
            reasons.add(key + " 정보 없음");
            return 0;
        }
        if (!"7".equals(String.valueOf(value))) {
            reasons.add(key + " 옵션 선택됨 (임시 비용 추가)");
            return 10000; // 임시 비용
        } else {
            reasons.add(key + " 옵션 선택 안됨");
        }
        return 0;
    }
}