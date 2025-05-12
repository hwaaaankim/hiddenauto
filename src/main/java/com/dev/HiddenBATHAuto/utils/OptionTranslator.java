package com.dev.HiddenBATHAuto.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductColor;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductOptionPosition;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OptionTranslator {

	public static Map<String, String> getLocalizedOptionMap(
		    String optionJson,
		    ProductSeriesRepository seriesRepo,
		    ProductRepository productRepo,
		    ProductColorRepository colorRepo,
		    ProductOptionPositionRepository optionRepo
		) {
		    try {
		        ObjectMapper objectMapper = new ObjectMapper();
		        Map<String, String> optionMap = objectMapper.readValue(optionJson, new TypeReference<>() {});
		        String category = optionMap.get("카테고리");
		        Map<String, Map<String, String>> stepMap = categoryOptionMaps.getOrDefault(category, Map.of());

		        Map<String, String> result = new LinkedHashMap<>();

		        for (Map.Entry<String, String> entry : optionMap.entrySet()) {
		            String key = entry.getKey();
		            String value = entry.getValue();
		            String localized = value;

		            try {
		                switch (key) {
		                    case "제품시리즈" -> localized = seriesRepo.findById(Long.parseLong(value))
		                            .map(Series::getName).orElse(value);
		                    case "제품" -> localized = productRepo.findById(Long.parseLong(value))
		                            .map(Product::getName).orElse(value);
		                    case "색상" -> localized = colorRepo.findById(Long.parseLong(value))
		                            .map(ProductColor::getProductColorSubject).orElse(value);
		                    case "LED 위치", "콘센트 옵션", "드라이걸이 옵션", "티슈홀캡(타공) 옵션" ->
		                        localized = optionRepo.findById(Long.parseLong(value))
		                            .map(ProductOptionPosition::getProductOptionPositionText).orElse(value);
		                    default -> localized = stepMap.getOrDefault(key, Map.of()).getOrDefault(value, value);
		                }
		            } catch (Exception e) {
		                System.out.println("⚠️ 변환 실패: " + key + " → " + value + ": " + e.getMessage());
		            }

		            result.put(key, localized);
		        }

		        return result;

		    } catch (Exception e) {
		        System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
		        return Map.of();
		    }
		}

	
	
    // category별 변환 맵 등록
    private static final Map<String, Map<String, Map<String, String>>> categoryOptionMaps = Map.of(
        "거울", mirrorOptionMap(),
        "하부장", lowOptionMap(),
        "상부장", topOptionMap(),
        "슬라이드장", slideOptionMap(),
        "플랩장", flapOptionMap()
    );

    public static Map<String, String> translateOptions(String optionJson) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> optionMap = objectMapper.readValue(optionJson, new TypeReference<>() {});

            String category = optionMap.get("카테고리");
            Map<String, Map<String, String>> stepMap = categoryOptionMaps.getOrDefault(category, Map.of());

            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : optionMap.entrySet()) {
                String stepLabel = entry.getKey();
                String value = entry.getValue();
                String converted = stepMap.getOrDefault(stepLabel, Map.of()).getOrDefault(value, value);
                result.put(stepLabel, converted);
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of(); // 실패 시 빈 맵 반환
        }
    }

    // 예시: 거울 카테고리 맵
    private static Map<String, Map<String, String>> mirrorOptionMap() {
        return Map.of(
            "설치 방향", Map.of("vertical", "세로형", "horizontal", "가로형"),
            "LED 추가", Map.of("add", "추가", "not_add", "추가 안함"),
            "전원 방식 선택", Map.of(
                "touch_three", "터치식 3컬러 변환",
                "direct_one", "직결식 단컬러",
                "touch_one", "터치식 단컬러"
            ),
            "LED 색상", Map.of(
                "one", "3000K(전구색/주황색)",
                "two", "4000K(주백색)",
                "three", "5700K(주광색/백색)"
            )
        );
    }

    private static Map<String, Map<String, String>> lowOptionMap() {
        return Map.ofEntries(
            Map.entry("세면대 형태", Map.of(
                "under", "언더볼",
                "dogi", "도기매립",
                "marble", "대리석",
                "body", "바디만(상판없음)"
            )),
            Map.entry("세면대 종류", Map.of(
                "1", "CL-603(사각)",
                "2", "CL-509(타원형)",
                "3", "제공 언더볼",
                "4", "TB-060 / E-60 / PL-3040 / PL-3060"
            )),
            Map.entry("문 추가여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함(바디만)"
            )),
            Map.entry("문 형태", Map.of(
                "slide", "슬라이드",
                "open", "여닫이",
                "drawer", "서랍식",
                "mixed", "혼합식"
            )),
            Map.entry("마구리 추가여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            )),
            Map.entry("마구리 설치방향", Map.of(
                "front", "전면",
                "front_left", "전면/좌측면",
                "front_right", "전면/우측면",
                "front_left_right", "전면/좌측면/우측면"
            )),
            Map.entry("상판 타공 유무", Map.of(
                "add", "타공함",
                "not_add", "타공안함"
            )),
            Map.entry("손잡이 추가", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            )),
            Map.entry("손잡이 종류", Map.of(
                "dolche", "히든 돌체 손잡이",
                "d195", "히든 D형(195) 손잡이",
                "half", "히든 하프 손잡이",
                "circle", "원형 손잡이",
                "d310", "히든 D형(310) 손잡이"
            )),
            Map.entry("손잡의 색상", Map.of(
                "one", "크롬",
                "two", "니켈",
                "three", "골드",
                "four", "블랙"
            )),
            Map.entry("걸레받이 추가여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            )),
            Map.entry("걸레받이 설치방향", Map.of(
                "front", "전면",
                "front_left", "전면/좌측면",
                "front_right", "전면/우측면",
                "front_left_right", "전면/좌측면/우측면"
            )),
            Map.entry("LED 추가 여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            )),
            Map.entry("LED 색상", Map.of(
                "one", "3000K(전구색/주황색)",
                "two", "4000K(주백색)",
                "three", "5700K(주광색/백색)"
            ))
        );
    }

    private static Map<String, Map<String, String>> topOptionMap() {
        return Map.ofEntries(
            Map.entry("문 추가", Map.of(
                "add", "추가",
                "not_add", "추가 안함(바디만)"
            )),
            Map.entry("손잡이 추가", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            )),
            Map.entry("손잡이 종류", Map.of(
                "dolche", "히든 돌체 손잡이",
                "d195", "히든 D형(195) 손잡이",
                "half", "히든 하프 손잡이",
                "circle", "원형 손잡이",
                "d310", "히든 D형(310) 손잡이"
            )),
            Map.entry("손잡의 색상", Map.of(
                "크롬", "크롬",
                "니켈", "니켈",
                "골드", "골드",
                "화이트", "화이트",
                "크림", "크림",
                "그레이", "그레이",
                "블랙", "블랙",
                "실버", "실버"
            )),
            Map.entry("LED 추가 여부", Map.of(
                "add", "추가",
                "not_add", "추가안함(조명공간없음)",
                "space", "추가안함(조명공간추가)"
            ))
        );
    }

    private static Map<String, Map<String, String>> slideOptionMap() {
        return Map.of(
            "문 추가여부", Map.of(
                "add", "추가함",
                "not_add", "추가하지않음(바디만)"
            ),
            "거울방향", Map.of(
                "left", "좌측경",
                "right", "우측경"
            ),
            "LED 추가 여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            ),
            "LED 색상", Map.of(
                "one", "3000K(전구색/주황색)",
                "two", "4000K(주백색)",
                "three", "5700K(주광색/백색)"
            )
        );
    }

    private static Map<String, Map<String, String>> flapOptionMap() {
        return Map.of(
            "문 추가", Map.of(
                "add", "추가",
                "not_add", "추가 안함(바디만)"
            ),
            "문 방향", Map.of(
                "left", "좌플랩",
                "right", "우플랩"
            ),
            "LED 추가 여부", Map.of(
                "add", "추가",
                "not_add", "추가 안함"
            ),
            "LED 색상", Map.of(
                "one", "3000K(전구색/주황색)",
                "two", "4000K(주백색)",
                "three", "5700K(주광색/백색)"
            )
        );
    }

}

