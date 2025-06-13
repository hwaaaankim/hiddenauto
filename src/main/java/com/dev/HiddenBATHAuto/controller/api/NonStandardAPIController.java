package com.dev.HiddenBATHAuto.controller.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.service.nonstandard.ProductMarkService;
import com.dev.HiddenBATHAuto.utils.OptionTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2")
public class NonStandardAPIController {

	private final ProductMarkService productMarkService;
    private final ObjectMapper objectMapper;
    private final ProductSeriesRepository productSeriesRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;
    private final ProductOptionPositionRepository productOptionPositionRepository;


    @PostMapping("/insertMark")
    public ResponseEntity<?> insertMark(@RequestBody Map<String, Object> payload,
                                        @AuthenticationPrincipal PrincipalDetails principal) {
    	try {
    		Member member = principal.getMember();

    		// 1. 원본 optionJson
    		Object optionJsonObj = payload.get("optionJson");
    		String rawJson = objectMapper.writeValueAsString(optionJsonObj);

    		// 2. 1차 번역된 localizedOptionJson
    		Object localizedJsonObj = payload.get("localizedOptionJson");
    		String firstTranslatedJson = objectMapper.writeValueAsString(localizedJsonObj);

    		// 3. 🔁 여기서 2차 번역을 적용 (→ Map<String, String>)
    		Map<String, String> finalTranslatedMap = OptionTranslator.getLocalizedOptionMap(
    			firstTranslatedJson,
    			productSeriesRepository,
    			productRepository,
    			productColorRepository,
    			productOptionPositionRepository
    		);
    		String localizedJson = objectMapper.writeValueAsString(finalTranslatedMap);

    		// 4. 저장
    		productMarkService.saveProductMark(member, rawJson, localizedJson);
    		return ResponseEntity.ok().build();

    	} catch (Exception e) {
    		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    			.body("저장 중 오류 발생: " + e.getMessage());
    	}
    }


}
