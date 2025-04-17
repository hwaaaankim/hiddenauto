package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.flap.FlapBasicPrice;
import com.dev.HiddenBATHAuto.model.calculate.flap.FlapOptionPrice;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.repository.caculate.flap.FlapBasicPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.flap.FlapOptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlapCalculateService {

    private final ProductRepository productRepository;
    private final FlapBasicPriceRepository flapBasicPriceRepository;
    private final FlapOptionPriceRepository flapOptionPriceRepository;

    public Map<String, Object> calculate(Map<String, Object> selection) {
        int mainPrice = 0;
        List<String> reasons = new ArrayList<>();

        Long productId = Long.parseLong(String.valueOf(selection.get("product")));
        Product product = productRepository.findById(productId).orElse(null);

        if (product == null) {
            reasons.add("📌 ❌ 제품 조회 실패 (ID: " + productId + ")");
            return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
        }

        String productName = product.getName();
        int basicWidth = product.getBasicWidth();
        int basicHeight = product.getBasicHeight();
        int basicDepth = product.getBasicDepth();

        reasons.add("📌 ✅ 제품 조회 성공: " + productName);
        reasons.add("📌 기본 사이즈: W" + basicWidth + ", H" + basicHeight + ", D" + basicDepth);

        FlapBasicPrice basicPrice = flapBasicPriceRepository.findByProductName(productName).orElse(null);
        if (basicPrice == null) {
            reasons.add("📌 ❌ 기본 가격 정보 없음: " + productName);
            return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
        }

        int base = basicPrice.getBasicPrice();
        reasons.add("📌 ✅ 기본 가격 조회됨 (" + productName + "): " + base + "원");

        int width = 0, height = 0, depth = 0;
        String sizeStr = (String) selection.get("size");
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
                reasons.add("📌 입력 사이즈: W" + width + ", H" + height + ", D" + depth);
            } catch (Exception e) {
                reasons.add("📌 ❌ 사이즈 파싱 실패");
            }
        }

        int materialWidth = ((basicWidth + 99) / 100) * 100;
        int inputWidth = ((width + 99) / 100) * 100;
        int widthDiff = inputWidth - materialWidth;

        if (widthDiff > 0) {
            int over1500 = Math.max(0, inputWidth - 1500);
            int under1500 = widthDiff - over1500;
            int widthCost = (under1500 / 100) * 15000 + (over1500 / 100) * 20000;
            base += widthCost;
            reasons.add("📌 넓이 기준: " + materialWidth + " → 입력값: " + width + ", 100단위 올림: " + inputWidth + ", 차이: " + widthDiff + " → 추가금: " + widthCost);
        } else {
            reasons.add("📌 넓이 초과 없음");
        }

        int materialHeight = ((basicHeight + 99) / 100) * 100;
        int inputHeight = ((height + 99) / 100) * 100;
        int heightDiff = inputHeight - materialHeight;

        if (heightDiff > 0) {
            int heightCost = (heightDiff / 100) * 20000;
            base += heightCost;
            reasons.add("📌 높이 기준: " + materialHeight + " → 입력값: " + height + ", 100단위 올림: " + inputHeight + ", 차이: " + heightDiff + " → 추가금: " + heightCost);
        } else {
            reasons.add("📌 높이 초과 없음");
        }

        if (depth > basicDepth) {
            int increased = (int) Math.round(base * 1.5);
            reasons.add("📌 깊이 기준: " + basicDepth + " → 입력값: " + depth + ", 증가로 1.5배 적용됨");
            base = increased;
        } else if (depth < basicDepth) {
            base += 30000;
            reasons.add("📌 깊이 기준: " + basicDepth + " → 입력값: " + depth + ", 감소로 3만원 추가됨");
        } else {
            reasons.add("📌 깊이 기준: " + basicDepth + " → 입력값: " + depth + ", 깊이 동일 → 추가금 없음");
        }

        String door = String.valueOf(selection.get("door"));
        if ("not_add".equals(door)) {
            base = (int) Math.round(base * 0.5);
            reasons.add("📌 문 옵션: 미포함 (기본가격의 50% 적용됨)");
        } else {
            reasons.add("📌 문 옵션: 포함됨");
        }

        int variablePrice = base;

        variablePrice += addOption(selection, "led", "하부LED", reasons);
        variablePrice += addOption(selection, "outletPosition", "콘센트", reasons);
        variablePrice += addOption(selection, "dryPosition", "드라이걸이", reasons);
        variablePrice += addOption(selection, "tissuePosition", "티슈홀캡", reasons);

        if ("add".equals(selection.get("handle"))) {
            String handleType = String.valueOf(selection.get("handletype"));
            reasons.add("📌 손잡이 추가됨 (종류: " + handleType + ")");
        } else {
            reasons.add("📌 손잡이 추가 없음");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", variablePrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);
        return result;
    }

    private int addOption(Map<String, Object> selection, String key, String label, List<String> reasons) {
        Object val = selection.get(key);
        if (val == null || "7".equals(String.valueOf(val))) {
            reasons.add("📌 " + label + " 선택 안됨");
            return 0;
        }
        FlapOptionPrice op = flapOptionPriceRepository.findByOptionName(label).orElse(null);
        if (op != null) {
            reasons.add("📌 " + label + " 가격 적용됨: " + op.getPrice());
            return op.getPrice();
        } else {
            reasons.add("📌 " + label + " 가격 조회 실패");
            return 0;
        }
    }
}