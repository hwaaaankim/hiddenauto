package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class LowCalculateService {

    public Map<String, Object> calculate(Map<String, Object> selection) {
        int mainPrice = 0;
        int variablePrice = 0;
        List<String> reasons = new ArrayList<>();

        // middleSort 체크
        Object middleSort = selection.get("middleSort");
        if (middleSort != null && "12".equals(String.valueOf(middleSort))) {
            reasons.add("middleSort 12번으로 별도 분기 처리 예정");
        } else {
            reasons.add("middleSort 일반 흐름 처리됨");
        }

        // 제품 ID 존재 확인
        if (selection.get("product") != null) {
            reasons.add("제품 ID 존재 확인 완료");
        } else {
            reasons.add("제품 ID 누락됨");
        }

        // 사이즈 파싱
        String sizeStr = (String) selection.get("size");
        int width = 0, height = 0, depth = 0;
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
                mainPrice += 100000; // 기본 사이즈 가격
                reasons.add("사이즈에 따른 기본 가격 적용됨");
            } catch (Exception e) {
                reasons.add("사이즈 정보 파싱 실패");
            }
        }

        // 세면대 형태
        String formofwash = (String) selection.get("formofwash");
        if (formofwash != null) {
            switch (formofwash) {
                case "under":
                	variablePrice += 20000;
                    reasons.add("세면대 종류 및 수량, 대리석색상 고려하여 가격 반영");
                    break;
                case "dogi":
                    variablePrice += 20000;
                    reasons.add("세면대 종류 및 수량, 대리석색상 고려하여 가격 반영");
                    break;
                case "marble":
                    variablePrice += 15000;
                    reasons.add("대리석 색상에 따른 추가 비용 발생");
                    break;
                case "body":
                    reasons.add("세면대 없음으로 관련 비용 제외됨");
                    break;
                default:
                    reasons.add("알 수 없는 세면대 형태 처리 안됨");
                    break;
            }
        } else {
            reasons.add("세면대 형태 정보 없음");
        }

        // door 관련 메시지
        String door = (String) selection.get("door");
        if ("add".equals(door)) {
            String formofdoor = (String) selection.get("formofdoor_other");
            if ("drawer".equals(formofdoor) || "mixed".equals(formofdoor)) {
                reasons.add("문의 형태에 따른 추가 설명 필요 (임시 메시지)");
            } else {
                reasons.add("문 추가됨");
            }
        } else {
            reasons.add("문 추가 없음 (not_add)");
        }

        // 마구리 계산
        String maguri = (String) selection.get("maguri");
        if ("add".equals(maguri)) {
            Integer sizeofmaguri = tryParseInt(selection.get("sizeofmaguri"));
            String direction = (String) selection.get("directionofmaguri");
            if (sizeofmaguri != null && sizeofmaguri > 150) {
                int length = calculateLengthByDirection(direction, width, depth);
                System.out.println("💡 마구리 대리석 길이: " + length);
                variablePrice += 10000; // 임시 금액
                reasons.add("마구리 사이즈 및 방향에 따른 추가 비용 발생");
            } else {
                reasons.add("마구리 150mm 이하로 추가 비용 없음");
            }
        } else {
            reasons.add("마구리 옵션 선택 안함 (not_add)");
        }

        // hole
        if ("add".equals(selection.get("hole"))) {
            reasons.add("상판 타공 있음 (추가 메시지)");
        } else {
            reasons.add("상판 타공 없음");
        }

        // handle
        if ("add".equals(selection.get("handle"))) {
            reasons.add("손잡이 옵션 선택됨 (추가 메시지)");
        } else {
            reasons.add("손잡이 옵션 선택 안됨");
        }

        // board
        if ("add".equals(selection.get("board"))) {
            String dir = (String) selection.get("directionofboard");
            int length = calculateLengthByDirection(dir, width, depth);
            variablePrice += length * 20;
            reasons.add("걸레받이 길이에 따른 비용 추가됨");
        } else {
            reasons.add("걸레받이 옵션 선택 안됨");
        }

        // led
        if ("add".equals(selection.get("led"))) {
            String pos = String.valueOf(selection.get("ledPosition"));
            int ledCount = ("5".equals(pos)) ? 2 : 1;
            variablePrice += ledCount * 15000;
            reasons.add("LED 수량에 따른 비용 추가됨");
        } else {
            reasons.add("LED 옵션 선택 안됨");
        }

        // outlet / dry / tissue
        variablePrice += addIfOptionPresent(selection, "outletPosition", reasons);
        variablePrice += addIfOptionPresent(selection, "dryPosition", reasons);
        variablePrice += addIfOptionPresent(selection, "tissuePosition", reasons);

        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", mainPrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);

        return result;
    }

    private Integer tryParseInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateLengthByDirection(String direction, int width, int depth) {
        if (direction == null) return 0;
        return switch (direction) {
            case "front" -> width;
            case "front_left", "front_right" -> width + depth;
            case "front_left_right" -> width + depth * 2;
            default -> 0;
        };
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
}
