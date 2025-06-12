package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/size")
public class SizeAPIController {
	
//	@GetMapping("/getSizeById")
//    public ResponseEntity<Map<String, Object>> getSizeById(@RequestParam Long sizeId) {
//        Map<String, Object> response = new HashMap<>();
//        try {
//            // DB에서 사이즈 조회
//            ProductSize productSize = productSizeRepository.findById(sizeId).orElse(null);
//            if (productSize != null) {
//                // "600*700*600" 형식의 텍스트를 파싱
//                String sizeText = productSize.getProductSizeText();
//                String[] dimensions = sizeText.split("\\*");
//
//                // 파싱된 값을 응답 데이터에 추가
//                if (dimensions.length > 0) {
//                    response.put("width", Integer.parseInt(dimensions[0].trim()));
//                }
//                if (dimensions.length > 1) {
//                    response.put("height", Integer.parseInt(dimensions[1].trim()));
//                }
//                if (dimensions.length > 2) {
//                    response.put("depth", Integer.parseInt(dimensions[2].trim()));
//                }
//
//                return ResponseEntity.ok(response);
//            } else {
//                // 사이즈 ID가 잘못된 경우
//                response.put("error", "Size not found for ID: " + sizeId);
//                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            response.put("error", "Failed to fetch size data: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
//        }
//    }
}
