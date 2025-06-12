package com.dev.HiddenBATHAuto.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.CartOrderRequestDTO;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.task.Cart;
import com.dev.HiddenBATHAuto.model.task.CartImage;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.CartRepository;
import com.dev.HiddenBATHAuto.service.order.CartService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class NonStandardOrderController {

	@Value("${spring.upload.path}")
	private String uploadPath;
	
	@Autowired
	private DeliveryMethodRepository deliveryMethodRepository;
	
	@Autowired
	private CartRepository cartRepository;
	
	@Autowired
	private CartService cartService;
	
	@GetMapping("/nonStandardOrderProduct")
	public String nonStandardOrderProduct() {

		return "front/order/nonStandardOrderProduct";
	}

	@GetMapping("/cart")
	public String cartPage(Model model, @AuthenticationPrincipal Member member) {
		List<Cart> cartList = cartService.findCartItems(member, false);
		model.addAttribute("cartItems", cartList);
		return "front/order/cart";
	}

	@PostMapping("/orderConfirm")
	public String orderConfirm(
	        @RequestParam String from,
	        @AuthenticationPrincipal PrincipalDetails principalDetails,
	        Model model,
	        @RequestParam(required = false) Boolean standard,
	        @RequestParam(required = false) String ordersJson, // from=cart
	        @RequestParam(required = false) String optionJson, // from=direct
	        @RequestParam(required = false) String localizedOptionJson,
	        @RequestParam(required = false) Integer quantity,
	        @RequestParam(required = false) Integer price,
	        @RequestParam(required = false) String additionalInfo,
	        @RequestParam(value = "files", required = false) List<MultipartFile> files
	) throws IOException {
	    Member member = principalDetails.getMember();
	    log.info("📥 [orderConfirm 호출됨] from={}", from);

	    List<Cart> orderList = new ArrayList<>();

	    // ✅ 1. 카트 → 넘겨받은 cartId만 처리
	    if ("cart".equals(from) && ordersJson != null) {
	        ObjectMapper mapper = new ObjectMapper();
	        List<CartOrderRequestDTO> orders = mapper.readValue(ordersJson, new TypeReference<>() {});

	        for (CartOrderRequestDTO dto : orders) {
	            Cart cart = cartRepository.findById(dto.getCartId())
	                    .orElseThrow(() -> new IllegalArgumentException("해당 cartId 없음: " + dto.getCartId()));
	            cart.setQuantity(dto.getQuantity());
	            cart.setDirectOrder(true); // 필요시 유지
	            cartRepository.save(cart);

	            log.info("🛒 [카트발주→업데이트] cartId: {}, quantity: {}", dto.getCartId(), dto.getQuantity());
	            orderList.add(cart);
	        }
	    }

	    // ✅ 2. 바로주문 → 새 Cart insert + 이미지 저장
	    if ("direct".equals(from) && optionJson != null && quantity != null && price != null) {
	        LocalDate today = LocalDate.now();
	        String datePath = today.toString(); // yyyy-MM-dd
	        String relativePath = String.format("/order/cart/%d/%s/", member.getId(), datePath);
	        String absolutePath = Paths.get(uploadPath, relativePath).toString();

	        Files.createDirectories(Paths.get(absolutePath)); // 폴더 생성

	        Cart cart = new Cart();
	        cart.setMember(member);
	        cart.setQuantity(quantity);
	        cart.setPrice(price);
	        cart.setOptionJson(optionJson);
	        cart.setLocalizedOptionJson(localizedOptionJson);
	        cart.setAdditionalInfo(additionalInfo);
	        cart.setDirectOrder(true); // 필요시 유지
	        cart.setStandard(standard); 
	        List<CartImage> imageList = new ArrayList<>();
	        if (files != null) {
	            for (MultipartFile file : files) {
	                if (file.isEmpty()) continue;

	                String uuid = UUID.randomUUID().toString();
	                String filename = uuid + "_" + file.getOriginalFilename();
	                String fullPath = Paths.get(absolutePath, filename).toString();

	                file.transferTo(Paths.get(fullPath));

	                CartImage image = new CartImage();
	                image.setCart(cart);
	                image.setImagePath(fullPath);
	                image.setImageUrl("/upload" + relativePath + filename);
	                imageList.add(image);

	                log.info("📂 파일 저장: {}", filename);
	            }
	        }

	        cart.setImages(imageList);
	        cartRepository.save(cart);
	        log.info("💬 [바로주문 저장 완료] cartId: {}, memberId: {}", cart.getId(), member.getId());

	        orderList.add(cart);
	    }

	    // ✅ 3. 회사 주소 정보
	    Company company = member.getCompany();
	    if (company != null) {
	        model.addAttribute("mainAddress", company.getRoadAddress());
	        model.addAttribute("detailAddress", company.getDetailAddress());
	        model.addAttribute("zipCode", company.getZipCode());
	        model.addAttribute("doName", company.getDoName());
	        model.addAttribute("siName", company.getSiName());
	        model.addAttribute("guName", company.getGuName());
	        model.addAttribute("point", company.getPoint());
	    } else {
	        model.addAttribute("mainAddress", "");
	        model.addAttribute("detailAddress", "");
	        model.addAttribute("zipCode", "");
	        model.addAttribute("doName", "");
	        model.addAttribute("siName", "");
	        model.addAttribute("guName", "");
	        model.addAttribute("point", "");
	    }

	    // ✅ 4. 현재 요청에 의해 만들어진 Cart 데이터만 전달
	    model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
	    model.addAttribute("from", from);
	    model.addAttribute("orderList", orderList); // 🔥 이전 directOrder 조회 제거됨

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

	@GetMapping("/productMark")
	public String productMark() {

		return "front/order/productMark";
	}
}
