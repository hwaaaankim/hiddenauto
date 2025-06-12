package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.StandardCalculateRequest;
import com.dev.HiddenBATHAuto.dto.StandardProductSeriesDTO;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductPriceRepository;
import com.dev.HiddenBATHAuto.service.standard.StandardProductSeriesService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/standard")
public class StandardAPIController {

	private final StandardProductSeriesService standardProductSeriesService;
	private final StandardProductPriceRepository priceRepository;

	@PostMapping("/calculate")
	public ResponseEntity<Map<String, Integer>> calculate(@RequestBody StandardCalculateRequest request) {

	    System.out.println("📥 [요청 수신]");
	    System.out.println("▶ productId = " + request.getProductId());
	    System.out.println("▶ sizeId = " + request.getSizeId());
	    System.out.println("▶ colorId = " + request.getColorId());
	    System.out.println("▶ tissue = " + request.getTissuePositionName());
	    System.out.println("▶ dry = " + request.getDryPositionName());
	    System.out.println("▶ outlet = " + request.getOutletPositionName());
	    System.out.println("▶ led = " + request.getLedPositionName());
	    System.out.println("▶ quantity = " + request.getQuantity());

	    // 기본 가격 조회
	    Optional<Integer> foundPrice = priceRepository.findPrice(
	        request.getProductId(),
	        request.getSizeId(),
	        request.getColorId()
	    );
	    int basePrice = foundPrice.orElse(0);
	    System.out.println("💰 기본 가격 = " + basePrice);

	    // 추가 옵션 가격 계산
	    int additionalPrice = 0;

	    if (!"추가안함".equals(request.getTissuePositionName())) {
	        additionalPrice += 10000;
	        System.out.println("➕ 티슈 추가: 10000");
	    }
	    if (!"추가안함".equals(request.getDryPositionName())) {
	        additionalPrice += 5000;
	        System.out.println("➕ 드라이 추가: 5000");
	    }
	    if (!"추가안함".equals(request.getOutletPositionName())) {
	        additionalPrice += 5000;
	        System.out.println("➕ 콘센트 추가: 5000");
	    }
	    if (!"추가안함".equals(request.getLedPositionName())) {
	        additionalPrice += 20000;
	        System.out.println("➕ LED 추가: 20000");
	    }

	    int quantity = Optional.ofNullable(request.getQuantity()).orElse(1);
	    int total = (basePrice + additionalPrice) * quantity;
	    System.out.println("🧾 최종 합계 = " + total);

	    return ResponseEntity.ok(Map.of(
	        "basePrice", basePrice,
	        "additionalPrice", additionalPrice,
	        "quantity", quantity,
	        "totalPrice", total
	    ));
	}
    
	@GetMapping("/standard-series")
	public List<StandardProductSeriesDTO> getSeriesByCategory(
			@RequestParam Long categoryId) {
	    return standardProductSeriesService.findByCategoryId(categoryId)
	             .stream().map(series -> new StandardProductSeriesDTO(series.getId(), series.getName()))
	             .collect(Collectors.toList());
	}
}
