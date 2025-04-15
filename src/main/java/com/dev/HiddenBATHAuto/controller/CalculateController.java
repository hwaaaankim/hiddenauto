package com.dev.HiddenBATHAuto.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.service.calculate.FlapCalculateService;
import com.dev.HiddenBATHAuto.service.calculate.LowCalculateService;
import com.dev.HiddenBATHAuto.service.calculate.MirrorCalculateService;
import com.dev.HiddenBATHAuto.service.calculate.SlideCalculateService;
import com.dev.HiddenBATHAuto.service.calculate.TopCalculateService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CalculateController {

	private final LowCalculateService lowService;
	private final TopCalculateService topService;
	private final MirrorCalculateService mirrorService;
	private final FlapCalculateService flapService;
	private final SlideCalculateService slideService;

	@PostMapping("/calculate")
	public ResponseEntity<Map<String, Object>> calculatePrice(@RequestBody Map<String, Object> selection) {
		System.out.println("📦 받은 선택 데이터: ");
		selection.forEach((k, v) -> System.out.println(" - " + k + ": " + v));

		// category value 추출
		Object categoryObj = selection.get("category");
		String categoryValue = "";

		if (categoryObj instanceof Map<?, ?> rawMap) {
			Object valueObj = rawMap.get("value");
			if (valueObj instanceof String str) {
				categoryValue = str;
			}
		}

		// 결과 초기화
		int mainPrice = 0;
		int variablePrice = 0;
		List<String> reasons = new ArrayList<>();

		// 카테고리 분기
		switch (categoryValue) {
		case "low" -> {
			Map<String, Object> result = lowService.calculate(selection);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = (int) result.get("variablePrice");

			Object reasonsObj = result.get("reasons");
			if (reasonsObj instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof String str) {
						reasons.add(str);
					}
				}
			}
		}
		case "top" -> {
			Map<String, Object> result = topService.calculate(selection);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = (int) result.get("variablePrice");

			Object reasonsObj = result.get("reasons");
			if (reasonsObj instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof String str) {
						reasons.add(str);
					}
				}
			}
		}
		case "mirror" -> {
			Map<String, Object> result = mirrorService.calculate(selection);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = (int) result.get("variablePrice");

			Object reasonsObj = result.get("reasons");
			if (reasonsObj instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof String str) {
						reasons.add(str);
					}
				}
			}
		}
		case "flap" -> {
			Map<String, Object> result = flapService.calculate(selection);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = (int) result.get("variablePrice");

			Object reasonsObj = result.get("reasons");
			if (reasonsObj instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof String str) {
						reasons.add(str);
					}
				}
			}
		}
		case "slide" -> {
			Map<String, Object> result = slideService.calculate(selection);
			mainPrice = (int) result.get("mainPrice");
			variablePrice = (int) result.get("variablePrice");

			Object reasonsObj = result.get("reasons");
			if (reasonsObj instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof String str) {
						reasons.add(str);
					}
				}
			}
		}
		
		default -> throw new IllegalArgumentException("지원하지 않는 category: " + categoryValue);
		
		}

		// 응답 구성
		Map<String, Object> response = new HashMap<>();
		response.put("mainPrice", mainPrice);
		response.put("variablePrice", variablePrice);
		response.put("reasons", reasons); // 💡 JS에서 반복문으로 출력하도록 수정됨

		return ResponseEntity.ok(response);
	}
}
