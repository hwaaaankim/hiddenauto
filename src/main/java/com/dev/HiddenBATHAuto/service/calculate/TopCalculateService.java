package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.top.TopBasicPrice;
import com.dev.HiddenBATHAuto.model.calculate.top.TopOptionPrice;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.repository.caculate.top.TopBasicPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.top.TopOptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopCalculateService {

	private final ProductRepository productRepository;
	private final TopBasicPriceRepository topBasicPriceRepository;
	private final TopOptionPriceRepository topOptionPriceRepository;

	public Map<String, Object> calculate(Map<String, Object> selection) {
		int mainPrice = 0;
		List<String> reasons = new ArrayList<>();

		Long productId = Long.parseLong(String.valueOf(selection.get("product")));
		Product product = productRepository.findById(productId).orElse(null);

		if (product == null) {
			reasons.add("❌ 제품 조회 실패");
			return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
		}

		String productName = product.getName();
		int basicWidth = product.getBasicWidth();
		int basicHeight = product.getBasicHeight();
		int basicDepth = product.getBasicDepth();

		reasons.add("✔️ 제품명: " + productName);
		reasons.add("✔️ 기본 사이즈: W" + basicWidth + ", H" + basicHeight + ", D" + basicDepth);

		TopBasicPrice basicPrice = topBasicPriceRepository.findByProductName(productName).orElse(null);
		if (basicPrice == null) {
			reasons.add("❌ 기본 가격 정보 없음: " + productName);
			return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
		}

		int base = basicPrice.getBasicPrice();
		reasons.add("✔️ 기본 가격 조회됨: " + base + "원");

		int width = 0, height = 0, depth = 0;
		String sizeStr = (String) selection.get("size");
		if (sizeStr != null) {
			try {
				String[] parts = sizeStr.split(",");
				width = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
				height = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
				depth = Integer.parseInt(parts[2].replaceAll("[^0-9]", ""));
				reasons.add("✔️ 입력 사이즈: W" + width + ", H" + height + ", D" + depth);
			} catch (Exception e) {
				reasons.add("❌ 사이즈 파싱 실패");
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
			reasons.add("✔️ 넓이 증가: 기준=" + materialWidth + ", 입력=" + width + " → 100단위 절상=" + inputWidth + ", 초과=" + widthDiff + ", 추가금=" + widthCost);
		} else {
			reasons.add("✔️ 넓이 초과 없음 (기준=" + materialWidth + ", 입력=" + width + ")");
		}

		int materialHeight = ((basicHeight + 99) / 100) * 100;
		int inputHeight = ((height + 99) / 100) * 100;
		int heightDiff = inputHeight - materialHeight;

		if (heightDiff > 0) {
			int heightCost = (heightDiff / 100) * 20000;
			base += heightCost;
			reasons.add("✔️ 높이 증가: 기준=" + materialHeight + ", 입력=" + height + " → 100단위 절상=" + inputHeight + ", 초과=" + heightDiff + ", 추가금=" + heightCost);
		} else {
			reasons.add("✔️ 높이 초과 없음 (기준=" + materialHeight + ", 입력=" + height + ")");
		}

		if (depth > basicDepth) {
			int increased = (int) Math.round(base * 1.5);
			reasons.add("✔️ 깊이 증가: 기준=" + basicDepth + ", 입력=" + depth + " → 1.5배 적용");
			base = increased;
		} else if (depth < basicDepth) {
			base += 30000;
			reasons.add("✔️ 깊이 감소: 기준=" + basicDepth + ", 입력=" + depth + " → 3만원 추가");
		} else {
			reasons.add("✔️ 깊이 동일 → 추가금 없음");
		}

		String door = String.valueOf(selection.get("door"));
		if ("not_add".equals(door)) {
			base = (int) Math.round(base * 0.5);
			reasons.add("✔️ 문 옵션: 미포함 → 50% 적용됨");
		} else {
			reasons.add("✔️ 문 옵션: 포함됨");
		}

		int variablePrice = base;

		if ("add".equals(selection.get("led"))) {
			String pos = String.valueOf(selection.get("ledPosition"));
			int ledCount = ("5".equals(pos)) ? 2 : 1;
			TopOptionPrice op = topOptionPriceRepository.findByOptionName("하부LED").orElse(null);
			if (op != null) {
				int added = ledCount * op.getPrice();
				variablePrice += added;
				reasons.add("✔️ LED 추가: 수량=" + ledCount + ", 단가=" + op.getPrice() + ", 추가금=" + added);
			} else {
				reasons.add("❌ LED 옵션 가격 조회 실패");
			}
		} else {
			reasons.add("✔️ LED 선택 안됨");
		}

		variablePrice += addOption(selection, "outletPosition", "콘센트", reasons);
		variablePrice += addOption(selection, "dryPosition", "드라이걸이", reasons);
		variablePrice += addOption(selection, "tissuePosition", "티슈홀캡", reasons);

		if ("add".equals(selection.get("handle"))) {
			String handleType = String.valueOf(selection.get("handletype"));
			reasons.add("✔️ 손잡이 추가됨 (종류: " + handleType + ")");
		} else {
			reasons.add("✔️ 손잡이 추가 없음");
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
			reasons.add("✔️ " + label + " 선택 안됨");
			return 0;
		}
		TopOptionPrice op = topOptionPriceRepository.findByOptionName(label).orElse(null);
		if (op != null) {
			reasons.add("✔️ " + label + " 가격 적용됨: " + op.getPrice());
			return op.getPrice();
		} else {
			reasons.add("❌ " + label + " 가격 조회 실패");
			return 0;
		}
	}
}