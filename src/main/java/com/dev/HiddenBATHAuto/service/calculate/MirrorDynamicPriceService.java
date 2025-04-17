package com.dev.HiddenBATHAuto.service.calculate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesEightRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesElevenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFiveLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFiveRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFourLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFourRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesNineRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesOneLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesOneRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSevenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSixLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSixRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesThreeLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesThreeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTwoLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTwoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MirrorDynamicPriceService {

	private final MirrorSeriesOneRepository mirrorSeriesOneRepository;
    private final MirrorSeriesOneLedRepository mirrorSeriesOneLedRepository;
    private final MirrorSeriesTwoRepository mirrorSeriesTwoRepository;
    private final MirrorSeriesTwoLedRepository mirrorSeriesTwoLedRepository;
    private final MirrorSeriesThreeRepository mirrorSeriesThreeRepository;
    private final MirrorSeriesThreeLedRepository mirrorSeriesThreeLedRepository;
    private final MirrorSeriesFourRepository mirrorSeriesFourRepository;
    private final MirrorSeriesFourLedRepository mirrorSeriesFourLedRepository;
    private final MirrorSeriesFiveRepository mirrorSeriesFiveRepository;
    private final MirrorSeriesFiveLedRepository mirrorSeriesFiveLedRepository;
    private final MirrorSeriesSixRepository mirrorSeriesSixRepository;
    private final MirrorSeriesSixLedRepository mirrorSeriesSixLedRepository;
    private final MirrorSeriesSevenRepository mirrorSeriesSevenRepository;
    private final MirrorSeriesEightRepository mirrorSeriesEightRepository;
    private final MirrorSeriesNineRepository mirrorSeriesNineRepository;
    private final MirrorSeriesTenRepository mirrorSeriesTenRepository;
    private final MirrorSeriesElevenRepository mirrorSeriesElevenRepository;

    public Map<String, Object> calculateUnstandardMirror(String productName, int width, int height, boolean led, List<String> reasons) {
        int baseWidth = mapToStandard(width);
        int baseHeight = mapToStandard(height);

        reasons.add("📥 입력 사이즈: W" + width + " / H" + height);
        reasons.add("📐 기준 사이즈로 매핑: W" + baseWidth + " / H" + baseHeight);
        reasons.add("⚠️ 규격 사이즈 불일치 → 비규격 가격 적용");

        String lowerName = productName.toLowerCase();
        int basePrice = 0;
        int ledPrice = 0;

        try {
            if (lowerName.contains("hd 100") || lowerName.contains("hd 101")) {
                reasons.add("🔍 시리즈: 무관 (HD 100 / HD 101)");
                basePrice = 150000;
                int wDiff = Math.max(0, (width - 600 + 99) / 100);
                int hDiff = Math.max(0, (height - 800 + 99) / 100);
                int extra = (wDiff + hDiff) * 5000;
                reasons.add("🔧 W/H 증가분 100mm 당 5000원 추가 x " + (wDiff + hDiff) + " = " + extra);
                basePrice += extra;
                reasons.add("💰 기본 가격: 150000 + 추가금 " + extra + " = " + basePrice);
            } else if (lowerName.contains("누드")) {
                reasons.add("🔍 시리즈: 시리즈01");
                basePrice = getPriceFromRepository(mirrorSeriesOneRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈01", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesOneLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈01_LED", reasons);
                }
            } else if (lowerName.contains("럭셔리각면")) {
                reasons.add("🔍 시리즈: 시리즈02");
                basePrice = getPriceFromRepository(mirrorSeriesTwoRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈02", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesTwoLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈02_LED", reasons);
                }
            } else if (lowerName.contains("럭셔리사각") || lowerName.contains("쥬얼리")) {
                reasons.add("🔍 시리즈: 시리즈03");
                basePrice = getPriceFromRepository(mirrorSeriesThreeRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈03", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesThreeLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈03_LED", reasons);
                }
            } else if (lowerName.contains("사이클")) {
                reasons.add("🔍 시리즈: 시리즈04");
                basePrice = getPriceFromRepository(mirrorSeriesFourRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈04", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesFourLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈04_LED", reasons);
                }
            } else if (lowerName.contains("슬림각면")) {
                reasons.add("🔍 시리즈: 시리즈05");
                basePrice = getPriceFromRepository(mirrorSeriesFiveRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈05", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesFiveLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈05_LED", reasons);
                }
            } else if (lowerName.contains("슬림사각")) {
                reasons.add("🔍 시리즈: 시리즈06");
                basePrice = getPriceFromRepository(mirrorSeriesSixRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈06", reasons);
                if (led) {
                    ledPrice = getPriceFromRepository(mirrorSeriesSixLedRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈06_LED", reasons);
                }
            } else if (lowerName.contains("시티 원형") || lowerName.contains("시티원형")) {
                if (led) {
                    reasons.add("🔍 시리즈: 시리즈08 (LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesEightRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈08", reasons);
                } else {
                    reasons.add("🔍 시리즈: 시리즈07 (비LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesSevenRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈07", reasons);
                }
            } else if (lowerName.contains("시티 트랙")) {
                if (led) {
                    reasons.add("🔍 시리즈: 시리즈09 (LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesNineRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈09", reasons);
                } else {
                    reasons.add("🔍 시리즈: 시리즈07 (비LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesSevenRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈07", reasons);
                }
            } else if (lowerName.contains("프로젝트") && productName.contains("5T")) {
                if (led) {
                    reasons.add("🔍 시리즈: 시리즈10 (LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesTenRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈10", reasons);
                } else {
                    reasons.add("🔍 시리즈: 시리즈11 (비LED)");
                    basePrice = getPriceFromRepository(mirrorSeriesElevenRepository.findByStandardWidth(baseWidth), baseHeight, "시리즈11", reasons);
                }
            } else {
                reasons.add("❌ 시리즈 매칭 실패 - 해당 제품 조건 없음");
            }

        } catch (Exception e) {
            reasons.add("❌ 가격 조회 중 오류 발생: " + e.getMessage());
        }

        if (ledPrice > 0) reasons.add("💡 LED 추가 금액: " + ledPrice + "원");
        reasons.add("💲 최종 가격 (기본 + LED): " + (basePrice + ledPrice) + "원");

        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", basePrice + ledPrice);
        result.put("variablePrice", basePrice + ledPrice);
        return result;
    }

    private int getPriceFromRepository(Object entity, int height, String label, List<String> reasons) {
        if (entity == null) {
            reasons.add("[" + label + "] 기준 넓이 데이터 없음");
            return 0;
        }
        try {
            String colName = "price" + height;
            int price = (int) entity.getClass().getMethod("get" + capitalize(colName)).invoke(entity);
            reasons.add("[" + label + "] 기준 너비에서 H" + height + " → 가격: " + price + "원");
            return price;
        } catch (Exception e) {
            reasons.add("[" + label + "] H" + height + "에 대한 가격 조회 실패");
            return 0;
        }
    }

    private int mapToStandard(int size) {
        return ((size + 99) / 100) * 100;
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
