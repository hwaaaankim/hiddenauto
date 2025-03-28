package com.dev.HiddenBATHAuto.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CalculateController {

	@PostMapping("/calculate")
	public ResponseEntity<Map<String, Object>> calculatePrice(
			@RequestBody Map<String, Object> selectedData
			) {
		// 임의의 가격 로직 (예: 제품, 사이즈, 색상 등 분석 가능)
		int mainPrice = (int)(Math.random() * (199999 - 50000 + 1)) + 50000;
		int variablePrice = (int)(Math.random() * (20000 - 10000 + 1)) + 10000;

		Map<String, Object> result = new HashMap<>();
		result.put("mainPrice", mainPrice);
		result.put("variablePrice", variablePrice);
		result.put("reason1", "사이즈에 따른 추가 요금 발생");
		result.put("reason2", "특수 색상 선택에 따른 변동");
		result.put("reason3", "옵션 구성에 따른 가격 조정");

		return ResponseEntity.ok(result);
	}
}
