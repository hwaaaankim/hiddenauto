package com.dev.HiddenBATHAuto.service.calculate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorStandardPrice;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorStandardPriceRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MirrorCalculateService {

	private final ProductRepository productRepository;
	private final MirrorStandardPriceRepository mirrorStandardPriceRepository;
	private final MirrorDynamicPriceService mirrorDynamicPriceService;

	public Map<String, Object> calculate(Map<String, Object> selection) {
		List<String> reasons = new ArrayList<>();
		int mainPrice = 0;
		int variablePrice = 0;

		Long productId = Long.parseLong(String.valueOf(selection.get("product")));
		Product product = productRepository.findById(productId).orElse(null);

		if (product == null) {
			reasons.add("❌ 제품 조회 실패");
			return Map.of("mainPrice", 0, "variablePrice", 0, "reasons", reasons);
		}

		String productName = product.getName();
		int basicWidth = product.getBasicWidth();
		int basicHeight = product.getBasicHeight();

		reasons.add("🔍 제품명: " + productName);
		reasons.add("📏 기준 사이즈: W" + basicWidth + ", H" + basicHeight);

		// 사용자 입력 사이즈 파싱
		String sizeStr = (String) selection.get("size");
		int inputWidth = 0, inputHeight = 0;
		try {
			String[] parts = sizeStr.split(",");
			inputWidth = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
			inputHeight = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
			reasons.add("📥 입력 사이즈: W" + inputWidth + ", H" + inputHeight);
		} catch (Exception e) {
			reasons.add("❌ 사이즈 파싱 실패");
		}

		// LED 여부
		boolean ledAdd = "add".equals(selection.get("normal"));
		reasons.add("💡 LED 선택 여부: " + (ledAdd ? "추가 (add)" : "미포함 (not_add)"));

		// ✅ 규격 사이즈 일치 여부 판단
		if (inputWidth == basicWidth && inputHeight == basicHeight) {
			MirrorStandardPrice standard = mirrorStandardPriceRepository.findByProductName(productName).orElse(null);
			if (standard != null) {
				reasons.add("✅ [규격 사이즈] 사용자 입력 사이즈가 제품 기본 사이즈와 정확히 일치합니다.");
				reasons.add("🔎 MirrorStandardPrice 테이블에서 제품명으로 조회됨");

				int price = ledAdd ? standard.getPriceLedOn() : standard.getPriceLedOff();
				mainPrice = variablePrice = price;

				if (ledAdd) {
					reasons.add("🔌 LED 포함 제품 - priceLedOn 컬럼 적용");
				} else {
					reasons.add("🔌 LED 미포함 제품 - priceLedOff 컬럼 적용");
				}
				reasons.add("💰 최종 가격: " + price + "원");
			} else {
				reasons.add("❌ MirrorStandardPrice 테이블에서 제품명을 기준으로 가격을 찾을 수 없음");
			}
		} else {
			reasons.add("⚠️ [비규격 사이즈] 입력한 사이즈가 기본 사이즈와 다릅니다 → 비규격 가격 로직 적용");
			Map<String, Object> result = mirrorDynamicPriceService.calculateUnstandardMirror(
					productName, inputWidth, inputHeight, ledAdd, reasons
			);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = mainPrice;
			reasons.add("💰 비규격 사이즈 최종 가격: " + mainPrice + "원");
		}

		Map<String, Object> result = new HashMap<>();
		result.put("mainPrice", mainPrice);
		result.put("variablePrice", variablePrice);
		result.put("reasons", reasons);
		return result;
	}
}