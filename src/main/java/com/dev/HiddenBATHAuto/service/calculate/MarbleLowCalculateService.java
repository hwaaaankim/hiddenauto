package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceOne;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceThree;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceTwo;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowLengthPrice;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowOptionPrice;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowType;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowWash;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceOneRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceThreeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceTwoRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowLengthPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowOptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowTypeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowWashRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarbleLowCalculateService {

    private final ProductRepository productRepository;
    private final MarbleLowTypeRepository marbleLowTypeRepository;
    private final MarbleLowBasePriceOneRepository baseOneRepo;
    private final MarbleLowBasePriceTwoRepository baseTwoRepo;
    private final MarbleLowBasePriceThreeRepository baseThreeRepo;
    private final MarbleLowWashRepository washRepo;
    private final MarbleLowLengthPriceRepository lengthRepo;
    private final MarbleLowOptionPriceRepository optionPriceRepository;

    public Map<String, Object> calculateForMarbleLow(Map<String, Object> selection) {
        int mainPrice = 0;
        int variablePrice;
        List<String> reasons = new ArrayList<>();

        // 1. Product 기준 사이즈 조회
        Long productId = Long.parseLong(String.valueOf(selection.get("product")));
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            reasons.add("제품 정보 조회 실패");
            return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
        }

        // 2. 사이즈 파싱
        String sizeStr = (String) selection.get("size");
        int width = 0, height = 0, depth = 0;
        if (sizeStr != null) {
            try {
                String[] parts = sizeStr.split(",");
                width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                reasons.add("사이즈 파싱 실패");
            }
        }

        int targetWidth = mapWidthToStandard(width);

        // 3. 대리석 타입 조회
        String marbleName = String.valueOf(selection.get("colorofmarble"));
        MarbleLowType marble = marbleLowTypeRepository.findByMarbleName(marbleName);
        if (marble == null) {
            reasons.add("대리석 타입 조회 실패");
            return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
        }

        int unitPrice = marble.getUnitPrice();
        int baseValue = 0;
        switch (unitPrice) {
            case 1 -> {
                MarbleLowBasePriceOne b1 = baseOneRepo.findByStandardWidth(targetWidth);
                baseValue = getDepthPrice(b1, depth);
            }
            case 2 -> {
                MarbleLowBasePriceTwo b2 = baseTwoRepo.findByStandardWidth(targetWidth);
                baseValue = getDepthPrice(b2, depth);
            }
            case 3 -> {
                MarbleLowBasePriceThree b3 = baseThreeRepo.findByStandardWidth(targetWidth);
                baseValue = getDepthPrice(b3, depth);
            }
        }
        if (height > 800) {
            baseValue += 35000;
            reasons.add("높이 800 초과로 35000 추가됨");
        }
        mainPrice += baseValue;
        reasons.add("대리석 타입: " + marbleName + ", 등급: " + unitPrice + ", 기준 W" + targetWidth + ", D" + depth + " → 기본 가격: " + baseValue);

        // 4. 세면대 수량
        Integer numberOfWash = tryParseInt(selection.get("numberofwash"));
        if (numberOfWash != null && numberOfWash >= 2) {
            MarbleLowWash wash = washRepo.findByStandardWidth(targetWidth);
            if (wash != null) {
                int washPrice = wash.getPrice() * (numberOfWash - 1);
                mainPrice += washPrice;
                reasons.add("세면대 수량: " + numberOfWash + ", 기준 너비: " + targetWidth + " → 추가금: " + washPrice);
            }
        }

        // 5. 마구리
        if ("add".equals(selection.get("maguri"))) {
            Integer size = tryParseInt(selection.get("sizeofmaguri"));
            if (size != null && size > 150) {
                String dir = String.valueOf(selection.get("directionofmaguri"));
                int length = calculateLengthByDirection(dir, width, depth);
                int mapped = mapWidthToStandard(length);
                MarbleLowLengthPrice len = lengthRepo.findByStandardWidth(mapped);
                if (len != null) {
                    int val = switch (unitPrice) {
                        case 2 -> len.getPrice2();
                        case 3 -> len.getPrice3();
                        default -> len.getPrice1();
                    } + len.getAdditionalFee();
                    mainPrice += val;
                    reasons.add("마구리 길이: " + length + "mm → 기준: " + mapped + "mm, 등급: " + unitPrice + " → 금액: " + val);
                }
            } else {
                reasons.add("마구리 사이즈 150 이하, 추가 없음");
            }
        } else {
            reasons.add("마구리 선택 안됨");
        }

        // 6. board
        if ("add".equals(selection.get("board"))) {
            String dir = String.valueOf(selection.get("directionofboard"));
            int len = calculateLengthByDirection(dir, width, depth);
            int price = len * 20;
            mainPrice += price;
            reasons.add("걸레받이 방향: " + dir + ", 길이: " + len + "mm → 금액: " + price);
        } else {
            reasons.add("걸레받이 선택 안됨");
        }
        
        // 7. LED
        if ("add".equals(selection.get("led"))) {
            String pos = String.valueOf(selection.get("ledPosition"));
            int count = ("5".equals(pos)) ? 2 : 1;
            MarbleLowOptionPrice op = optionPriceRepository.findByOptionName("하부LED");
            if (op != null) {
                int total = op.getPrice() * count;
                mainPrice += total;
                reasons.add("LED 수량: " + count + ", 단가: " + op.getPrice() + " → 총: " + total);
            }
        } else {
            reasons.add("LED 선택 안됨");
        }

        // 8. 기타 옵션
        mainPrice += addOption(selection, "outletPosition", "콘센트", reasons);
        mainPrice += addOption(selection, "dryPosition", "드라이걸이", reasons);
        mainPrice += addOption(selection, "tissuePosition", "티슈홀캡", reasons);

        // 9. 메시지 전용 항목들 (가격 영향 없음)
        String door = String.valueOf(selection.get("door"));
        if ("add".equals(door)) {
            String formSlide = String.valueOf(selection.get("formofdoor_slide"));
            String formOther = String.valueOf(selection.get("formofdoor_other"));
            String form = (!"null".equals(formSlide) && formSlide != null) ? formSlide : formOther;
            reasons.add("문 추가됨 (형태: " + form + ", 수량: " + selection.get("numberofdoor") + ")");
        } else {
            reasons.add("문 추가 없음");
        }

        String handle = String.valueOf(selection.get("handle"));
        if ("add".equals(handle)) {
            reasons.add("손잡이 추가됨 (종류: " + selection.get("handletype") + ")");
        } else {
            reasons.add("손잡이 추가 없음");
        }

        String hole = String.valueOf(selection.get("hole"));
        if ("add".equals(hole)) {
            reasons.add("상판 타공 있음");
        } else {
            reasons.add("상판 타공 없음");
        }
        
        variablePrice = mainPrice;
        Map<String, Object> result = new HashMap<>();
        result.put("mainPrice", mainPrice);
        result.put("variablePrice", variablePrice);
        result.put("reasons", reasons);
        return result;
    }

    private int mapWidthToStandard(int width) {
        return ((width / 100) * 100) + 100;
    }

    private int getDepthPrice(Object base, int depth) {
        if (base instanceof MarbleLowBasePriceOne b)
            return selectDepthPrice(b.getPrice500(), b.getPrice600(), b.getPrice700(), depth);
        if (base instanceof MarbleLowBasePriceTwo b)
            return selectDepthPrice(b.getPrice500(), b.getPrice600(), b.getPrice700(), depth);
        if (base instanceof MarbleLowBasePriceThree b)
            return selectDepthPrice(b.getPrice500(), b.getPrice600(), b.getPrice700(), depth);
        return 0;
    }

    private int selectDepthPrice(int p500, int p600, int p700, int depth) {
        if (depth <= 500) return p500;
        else if (depth <= 600) return p600;
        else return p700;
    }

    private int calculateLengthByDirection(String dir, int width, int depth) {
        return switch (dir) {
            case "front" -> width;
            case "front_left", "front_right" -> width + depth;
            case "front_left_right" -> width + depth * 2;
            default -> 0;
        };
    }

    private int addOption(Map<String, Object> selection, String key, String label, List<String> reasons) {
        Object val = selection.get(key);
        if (val == null || "7".equals(String.valueOf(val))) {
            reasons.add(label + " 선택 안됨");
            return 0;
        }
        MarbleLowOptionPrice op = optionPriceRepository.findByOptionName(label);
        if (op != null) {
            reasons.add(label + " 금액 적용: " + op.getPrice());
            return op.getPrice();
        } else {
            reasons.add(label + " 가격 조회 실패");
            return 0;
        }
    }
    private Integer tryParseInt(Object value) {
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (Exception e) {
			return null;
		}
	}
}
