package com.dev.HiddenBATHAuto.controller.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.task.Cart;
import com.dev.HiddenBATHAuto.model.task.CartImage;
import com.dev.HiddenBATHAuto.repository.order.CartRepository;
import com.dev.HiddenBATHAuto.service.order.CartService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Slf4j
public class CartApiController {

	private final CartService cartService;
	private final CartRepository cartRepository;

	@PostMapping("/insertCart")
	public ResponseEntity<?> insertCart(
		@RequestParam int quantity,
		@RequestParam int price,
		@RequestParam String optionJson,
		@RequestParam String localizedOptionJson,
		@RequestParam boolean standard,
		@RequestParam(required = false) String additionalInfo,
		@RequestParam(required = false) List<MultipartFile> files,
		@AuthenticationPrincipal PrincipalDetails principalDetails
	) {
		try {
			if (principalDetails == null || principalDetails.getMember() == null) {
		        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
		    }

		    Member member = principalDetails.getMember();
			cartService.saveCart(member, quantity, price, optionJson, localizedOptionJson, additionalInfo, files, standard);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body("장바구니 저장 실패");
		}
	}
	
	/**
     * 현재 로그인된 사용자의 일반 장바구니 조회
     */
    @GetMapping("/cartSelect")
    public ResponseEntity<List<Cart>> selectCart(@AuthenticationPrincipal PrincipalDetails principalDetails) {
    	System.out.println("cart 조회");
    	Member member = principalDetails.getMember();
        if (member == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(cartService.findCartsByMember(member));
    }

    /**
     * 장바구니 항목 삭제 (본인 소유 확인)
     */
    @GetMapping("/cartDelete/{cartId}")
    public ResponseEntity<Void> deleteCart(@PathVariable Long cartId,
                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
    	Member member = principalDetails.getMember();
        if (member == null) {
            return ResponseEntity.status(401).build();
        }
        cartService.deleteCartById(cartId, member);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/cartDeleteAll")
    public ResponseEntity<Void> deleteAllCarts(@RequestBody List<Long> cartIds,
                                               @AuthenticationPrincipal PrincipalDetails principalDetails) {
        Member member = principalDetails.getMember();
        if (member == null) {
            return ResponseEntity.status(401).build();
        }

        for (Long cartId : cartIds) {
            cartService.deleteCartById(cartId, member);
        }

        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/cart/discardDirectOrders")
    public ResponseEntity<Void> discardDirectOrders(
            @RequestBody List<Long> cartIds,
            @AuthenticationPrincipal PrincipalDetails principalDetails) {
    	System.out.println(cartIds.toString());
        Member member = principalDetails.getMember();

        for (Long cartId : cartIds) {
            cartRepository.findById(cartId).ifPresent(cart -> {
                if (cart.getMember().equals(member) && Boolean.TRUE.equals(cart.isDirectOrder())) {
                    // 이미지 파일 삭제
                    if (cart.getImages() != null) {
                        for (CartImage image : cart.getImages()) {
                            try {
                                Files.deleteIfExists(Paths.get(image.getImagePath()));
                                log.info("🗑️ 이미지 삭제 완료: {}", image.getImagePath());
                            } catch (IOException e) {
                                log.warn("⚠️ 이미지 삭제 실패: {}", image.getImagePath(), e);
                            }
                        }
                    }

                    cartRepository.delete(cart);
                    log.info("🗑️ directOrder 카트 삭제 완료: cartId={}", cartId);
                }
            });
        }

        return ResponseEntity.ok().build();
    }

}
