package com.dev.HiddenBATHAuto.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class NonStandardOrderController {

	@GetMapping("/nonStandardOrderProduct")
	public String nonStandardOrderProduct() {

		return "front/order/nonStandardOrderProduct";
	}

	@GetMapping("/cart")
	public String cart() {

		return "front/order/cart";
	}

	@GetMapping("/orderConfirm")
	public String orderConfirm() {

		return "front/order/orderConfirm";
	}

	@PostMapping("/modeling")
    public String showModelingView(@RequestParam Map<String, String> formData, Model model) {
        return processDataAndReturnView(formData, model, "front/order/modeling");
    }

    @PostMapping("/blueprint")
    public String showBluePrintView(@RequestParam Map<String, String> formData, Model model) {
        return processDataAndReturnView(formData, model, "front/order/blueprint");
    }

    private String processDataAndReturnView(Map<String, String> formData, Model model, String viewName) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // JSON 문자열을 Map<String, Object>로 변환
            Map<String, Object> dataMap = objectMapper.readValue(formData.get("data"),
                    new TypeReference<Map<String, Object>>() {});

            // 사이즈 값 처리
            if (dataMap.containsKey("size")) {
                String sizeValue = dataMap.get("size").toString();
                if (sizeValue.contains("넓이")) {
                    // 사용자가 직접 입력한 경우
                    processManualSizeInput(sizeValue, dataMap);
                } else {
                    // DB에서 사이즈 조회
                    // processSizeFromDatabase(sizeValue, dataMap);
                }
            }

            model.addAttribute("selectedData", dataMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return viewName;
    }

    // 사용자가 직접 입력한 사이즈 처리
    private void processManualSizeInput(String sizeValue, Map<String, Object> dataMap) {
        try {
            String[] sizeParts = sizeValue.split(","); // "넓이: ___, 높이: ___, 깊이: ___"
            for (String part : sizeParts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    if (key.equals("넓이")) {
                        dataMap.put("width", Integer.parseInt(value));
                    } else if (key.equals("높이")) {
                        dataMap.put("height", Integer.parseInt(value));
                    } else if (key.equals("깊이")) {
                        dataMap.put("depth", Integer.parseInt(value));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    // DB에서 사이즈 조회 및 처리
//    private void processSizeFromDatabase(String sizeId, Map<String, Object> dataMap) {
//        try {
//            Long id = Long.parseLong(sizeId);
//            ProductSize productSize = productSizeRepository.findById(id).orElse(null);
//            if (productSize != null) {
//                String sizeText = productSize.getProductSizeText(); // 예: "1200*500*300"
//                String[] dimensions = sizeText.split("\\*");
//                if (dimensions.length > 0) {
//                    dataMap.put("width", Integer.parseInt(dimensions[0]));
//                }
//                if (dimensions.length > 1) {
//                    dataMap.put("height", Integer.parseInt(dimensions[1]));
//                }
//                if (dimensions.length > 2) {
//                    dataMap.put("depth", Integer.parseInt(dimensions[2]));
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

	@GetMapping("/productMark")
	public String productMark() {

		return "front/order/productMark";
	}
}
