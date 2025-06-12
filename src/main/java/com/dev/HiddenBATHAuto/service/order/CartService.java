package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Cart;
import com.dev.HiddenBATHAuto.model.task.CartImage;
import com.dev.HiddenBATHAuto.repository.order.CartRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

	private final CartRepository cartRepository;

	@Value("${spring.upload.path}")
	private String uploadPath;
	
	 /**
     * 기본 장바구니 항목만 조회 (directOrder = false)
     */
    public List<Cart> findCartsByMember(Member member) {
        return cartRepository.findByMemberAndDirectOrder(member, false);
    }

    /**
     * 장바구니 항목 단일 삭제 (본인 소유 확인 포함)
     */
    public void deleteCartById(Long cartId, Member member) {
        Cart cart = cartRepository.findByIdAndMember(cartId, member)
                .orElseThrow(() -> new IllegalArgumentException("해당 장바구니 항목이 존재하지 않거나 권한이 없습니다."));
        cartRepository.delete(cart);
    }
    
	public void saveCart(Member member, int quantity, int price, String optionJson, String localizedOptionJson,
						 String additionalInfo, List<MultipartFile> files, Boolean standard) throws IOException {

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
		cart.setDirectOrder(false); // 장바구니 저장
		cart.setStandard(standard); // ✅ 프론트에서 받은 값 사용
		
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
				image.setImagePath(fullPath); // 디스크 저장 경로
				image.setImageUrl("/upload" + relativePath + filename); // 웹 접근용 경로
				image.setUploadedAt(LocalDateTime.now()); // ✅ 추가
				imageList.add(image);
			}
		}

		cart.setImages(imageList);
		cartRepository.save(cart);
	}
	
	public List<Cart> findCartItems(Member member, boolean isDirectOrder) {
		return cartRepository.findByMemberAndDirectOrder(member, isDirectOrder);
	}

}
