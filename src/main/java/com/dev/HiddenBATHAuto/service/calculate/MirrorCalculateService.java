package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class MirrorCalculateService {

	public Map<String, Object> calculate(Map<String, Object> selection) {
        int mainPrice = 0;
        int variablePrice = 0;
        List<String> reasons = new ArrayList<>();

        int middleSort = parseInt(selection.get("middleSort"));
        int productId = parseInt(selection.get("product"));
        String productName = getProductNameById(productId); // 향후 DB 연동
        String series = determineSeries(middleSort, productName);

        reasons.add("제품 시리즈: " + series);

        int width = 0, height = 0;
        String sizeStr = (String) selection.get("size");
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                reasons.add("입력된 사이즈: " + width + "x" + height);
            } catch (Exception e) {
                reasons.add("사이즈 파싱 실패");
            }
        }

        boolean ledAdd = "add".equals(selection.get("normal"));

        switch (series) {
            case "시리즈1" -> processSeries(width, height, ledAdd, reasons, "시리즈1", true);
            case "시리즈2" -> processSeries(width, height, ledAdd, reasons, "시리즈2", false);
            case "시리즈3" -> processSeries(width, height, ledAdd, reasons, "시리즈3", false);
            case "시리즈4" -> processSeries(width, height, ledAdd, reasons, "시리즈4", true);
            case "시리즈5" -> processSeries(width, height, ledAdd, reasons, "시리즈5", false);
            case "시리즈6" -> processSeries(width, height, ledAdd, reasons, "시리즈6", false);
            case "시리즈7" -> processSeries(width, height, ledAdd, reasons, "시리즈7", false);
            case "시리즈8" -> processSeries(width, height, ledAdd, reasons, "시리즈8", true);
            default -> reasons.add("해당 시리즈 없음");
        }

        // 임시 가격 적용
        mainPrice = 150000;
        variablePrice = 20000;

        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", mainPrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);

        return result;
    }

    private void processSeries(int width, int height, boolean ledAdd, List<String> reasons, String seriesName, boolean isExactSizeRequired) {
        int standardWidth = 600;
        int standardHeight = 800;

        if (width == standardWidth && height == standardHeight) {
            reasons.add(seriesName + ": 규격 사이즈 일치");
            if (ledAdd) {
                reasons.add(seriesName + ": LED 추가됨 - 규격사이즈용 가격 적용");
            } else {
                reasons.add(seriesName + ": LED 미포함 - 규격사이즈 기본 가격 적용");
            }
        } else {
            reasons.add(seriesName + ": 사이즈 불일치");
            if (ledAdd) {
                reasons.add(seriesName + ": LED 추가됨 - 비규격사이즈용 가격 적용");
            } else {
                reasons.add(seriesName + ": LED 미포함 - 비규격사이즈 기본 가격 적용");
            }
        }
    }

    private String determineSeries(int middleSort, String productName) {
        if (middleSort == 25 && productName.contains("시티")) {
            return "시리즈8";
        }
        return switch (middleSort) {
            case 23 -> "시리즈1";
            case 24 -> "시리즈2";
            case 25 -> "시리즈3";
            case 26 -> "시리즈4";
            case 27 -> "시리즈5";
            case 28 -> "시리즈6";
            case 29 -> "시리즈7";
            default -> "기타";
        };
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
        return productId == 999 ? "시티 미러" : "기본 제품";
    }
}