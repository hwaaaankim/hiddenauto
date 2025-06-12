package com.dev.HiddenBATHAuto.service.order;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Cart;
import com.dev.HiddenBATHAuto.model.task.CartImage;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.order.CartRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.utils.OptionTranslator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderProcessingService {

	private final TaskRepository taskRepository;
	private final ProductSeriesRepository productSeriesRepository;
	private final ProductRepository productRepository;
	private final ProductColorRepository productColorRepository;
	private final ProductOptionPositionRepository productOptionPositionRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final DeliveryMethodRepository deliveryMethodRepository;
	private final CompanyRepository companyRepository;
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final CartRepository cartRepository;

	@Value("${spring.upload.path}")
	private String uploadRootPath;
	
	public String createTaskWithOrders(Member member, List<OrderRequestItemDTO> items, int pointUsed) throws JsonProcessingException {
	    System.out.println("📥 createTaskWithOrders 시작");
	    System.out.println("➡ 주문 수 : " + items.size());
	    System.out.println("➡ 포인트 사용 : " + pointUsed);
	    
	    Task task = new Task();
	    task.setRequestedBy(member);
	    task.setStatus(TaskStatus.REQUESTED);
	    task.setCustomerNote("임시 고객 메모");
	    task.setInternalNote("임시 내부 메모");

	    List<Order> orderList = new ArrayList<>();
	    int totalPrice = 0;

	    for (OrderRequestItemDTO dto : items) {
	        System.out.println("🚚 주문 처리 시작 → Cart ID: " + dto.getCartId());

	        Cart cart = cartRepository.findById(dto.getCartId())
	            .orElseThrow(() -> {
	                System.out.println("❌ 존재하지 않는 카트 ID: " + dto.getCartId());
	                return new IllegalArgumentException("존재하지 않는 카트 ID: " + dto.getCartId());
	            });

	        Order order = new Order();
	        order.setTask(task);
	        order.setStandard(cart.isStandard());
	        
	        int quantity = cart.getQuantity();
	        int productCost = cart.getPrice();
	        int deliveryPrice = dto.getDeliveryPrice();
	        int singleOrderTotal = quantity * productCost + deliveryPrice;
	        totalPrice += singleOrderTotal;

	        System.out.printf("  - 수량: %d, 제품단가: %d, 배송비: %d, 합계: %d\n", quantity, productCost, deliveryPrice, singleOrderTotal);

	        order.setQuantity(quantity);
	        order.setProductCost(productCost);
	        order.setZipCode(dto.getZipCode());
	        order.setRoadAddress(dto.getMainAddress());
	        order.setDetailAddress(dto.getDetailAddress());
	        order.setPreferredDeliveryDate(dto.getPreferredDeliveryDate().atStartOfDay());
	        order.setOrderComment(cart.getAdditionalInfo());
	        refineAddressFromFullRoad(order);
	        assignDeliveryHandlerIfPossible(order);

	        DeliveryMethod method = deliveryMethodRepository.findById(dto.getDeliveryMethodId())
	            .orElseThrow(() -> {
	                System.out.println("❌ 존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId());
	                return new IllegalArgumentException("존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId());
	            });
	        order.setDeliveryMethod(method);

	        Map<String, Object> localizedOptionMap = objectMapper.readValue(cart.getLocalizedOptionJson(), new TypeReference<>() {});
	        String categoryName = Optional.ofNullable(localizedOptionMap.get("카테고리"))
	            .map(Object::toString)
	            .orElseThrow(() -> new IllegalArgumentException("카테고리 정보가 없습니다."));

	        if ("거울".equals(categoryName)) {
	            String ledOption = Optional.ofNullable(localizedOptionMap.get("LED 추가")).map(Object::toString).orElse("");
	            categoryName = "add".equals(ledOption) ? "LED거울" : "거울";
	        }

	        System.out.println("📦 제품 카테고리: " + categoryName);

	        TeamCategory productCategory = teamCategoryRepository.findByName(categoryName).orElse(null);
	        if (productCategory == null) {
	            System.out.println("⚠ 기본 카테고리 사용: 배정없음");
	            productCategory = teamCategoryRepository.findByName("배정없음")
	                .orElseThrow(() -> new IllegalStateException("기본 TeamCategory '배정없음'이 DB에 없습니다."));
	        }
	        order.setProductCategory(productCategory);
	        order.setStatus(OrderStatus.REQUESTED);

	        OrderItem orderItem = new OrderItem();
	        orderItem.setOrder(order);
	        orderItem.setProductName("임시 제품명");
	        orderItem.setQuantity(quantity);

	        try {
	            Map<String, String> localizedMap = OptionTranslator.getLocalizedOptionMap(
	                cart.getLocalizedOptionJson(),
	                productSeriesRepository,
	                productRepository,
	                productColorRepository,
	                productOptionPositionRepository
	            );
	            String convertedJson = objectMapper.writeValueAsString(localizedMap);
	            orderItem.setOptionJson(convertedJson);
	        } catch (Exception e) {
	            System.out.println("❌ 옵션 변환 실패: " + e.getMessage());
	            throw new RuntimeException("옵션 변환 실패", e);
	        }

	        order.setOrderItem(orderItem);

	        List<OrderImage> orderImages = new ArrayList<>();
	        String today = LocalDate.now().toString();
	        Long memberId = member.getId();
	        String destDir = String.format("order/order/%d/%s/request", memberId, today);
	        File destFolder = Paths.get(uploadRootPath, destDir).toFile();
	        if (!destFolder.exists()) destFolder.mkdirs();

	        for (CartImage cartImg : cart.getImages()) {
	            try {
	                Path imagePath = Paths.get(cartImg.getImagePath());
	                Path fullSourcePath = imagePath.isAbsolute()
	                    ? imagePath
	                    : Paths.get(uploadRootPath, cartImg.getImagePath());

	                File source = fullSourcePath.toFile();

	                if (!source.exists()) {
	                    System.err.println("❌ 원본 이미지 없음: " + cartImg.getImagePath());
	                    continue;
	                }

	                String newFilename = UUID.randomUUID() + "_" + imagePath.getFileName().toString();
	                File target = Paths.get(destFolder.getPath(), newFilename).toFile();

	                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

	                OrderImage newImg = new OrderImage();
	                newImg.setOrder(order);
	                newImg.setType("CUSTOMER");
	                newImg.setFilename(newFilename);
	                newImg.setPath(destDir);
	                newImg.setUrl("/upload/" + destDir.replace("\\", "/") + "/" + newFilename);
	                newImg.setUploadedAt(cartImg.getUploadedAt());
	                orderImages.add(newImg);
	                System.out.println("🖼 이미지 복사 완료: " + newFilename);

	            } catch (IOException e) {
	                System.err.println("❌ 이미지 복사 실패: " + e.getMessage());
	                e.printStackTrace();
	            }
	        }


	        order.setOrderImages(orderImages);
	        orderList.add(order);
	        cartRepository.delete(cart);
	        System.out.println("✅ 주문 항목 저장 완료");
	    }

	    Company company = companyRepository.findById(member.getCompany().getId())
	        .orElseThrow(() -> new IllegalArgumentException("회사 정보를 찾을 수 없습니다."));

	    int currentPoint = company.getPoint();
	    int remainingPoint = currentPoint - pointUsed;
	    if (remainingPoint < 0) {
	        System.out.println("❌ 포인트 부족: 보유=" + currentPoint + ", 사용=" + pointUsed);
	        throw new IllegalStateException("사용 가능한 포인트가 부족합니다. 현재 보유: " + currentPoint + "P");
	    }

	    int rewardPoint = (int) (totalPrice * 0.01);
	    company.setPoint(remainingPoint + rewardPoint);
	    System.out.printf("💰 총금액: %d원, 보유포인트: %d → 잔여포인트: %d, 적립예정: %dP\n", totalPrice, currentPoint, remainingPoint, rewardPoint);

	    task.setOrders(orderList);
	    task.setTotalPrice(totalPrice);
	    taskRepository.save(task);

	    System.out.println("🎉 발주 저장 완료!");
	    return "발주가 완료되었습니다.";
	}

	private void refineAddressFromFullRoad(Order order) {
		String full = order.getRoadAddress();
		if (full == null || full.isBlank())
			return;

		String[] tokens = full.trim().split("\\s+");

		String doName = "";
		String siName = "";
		String guName = "";

		if (tokens.length >= 1) {
			doName = tokens[0]; // 무조건 첫 단어는 도 (서울, 경기, 광주 등)
		}

		// 이후 토큰에서 '시'와 '구'를 확인
		for (int i = 1; i < tokens.length; i++) {
			String word = tokens[i];

			if (word.endsWith("시") && siName.isBlank()) {
				siName = word;
			} else if (word.endsWith("구") && guName.isBlank()) {
				guName = word;
			}

			if (!siName.isBlank() && !guName.isBlank())
				break;
		}

		// 특수 케이스: 서울/광주/부산 등은 시 자체가 없고 구만 있는 경우
		if (siName.isBlank() && guName.isBlank() && tokens.length >= 2) {
			guName = tokens[1]; // 서울 관악구 → 관악구
		}

		order.setDoName(doName);
		order.setSiName(siName);
		order.setGuName(guName);
	}

	private void assignDeliveryHandlerIfPossible(Order order) {
		String doName = order.getDoName();
		String siName = order.getSiName();
		String guName = order.getGuName();

		System.out.println("📦 [주소 파싱 결과]");
		System.out.println("- 도 : " + doName);
		System.out.println("- 시 : " + siName);
		System.out.println("- 구 : " + guName);

		if (guName == null || doName == null) {
			System.out.println("❌ 구 또는 도 정보 부족. 배정 중단");
			return;
		}

		String siKeyword = (siName == null || siName.isBlank()) ? null : siName;

		try {
			Optional<District> districtOpt = districtRepository.findByAddressPartsSingleNative(guName, doName,
					siKeyword);

			if (districtOpt.isEmpty()) {
				System.out.println("❌ 지역 일치 실패. 배송 담당자 배정 불가");
				return;
			}

			District district = districtOpt.get();

			System.out.println("✅ 매칭된 District: " + district.getName() + ", Province: "
					+ (district.getProvince() != null ? district.getProvince().getName() : "null") + ", City: "
					+ (district.getCity() != null ? district.getCity().getName() : "null"));

			List<MemberRegion> matchedRegions = memberRegionRepository.findByDistrict(district);
			System.out.println("🔎 MemberRegion 조회 결과: " + matchedRegions.size() + "개");

			List<Member> deliveryCandidates = new ArrayList<>();
			for (MemberRegion region : matchedRegions) {
				Member m = region.getMember();
				String teamName = m.getTeam() != null ? m.getTeam().getName() : null;

				System.out.println("➡️ 후보자: " + m.getUsername() + ", 팀: " + teamName);

				if (m.getRole() == MemberRole.INTERNAL_EMPLOYEE && "배송팀".equals(teamName)) {
					deliveryCandidates.add(m);
				}
			}

			if (!deliveryCandidates.isEmpty()) {
				Member selected = deliveryCandidates.get((int) (Math.random() * deliveryCandidates.size()));
				order.setAssignedDeliveryHandler(selected);
				order.setAssignedDeliveryTeam(selected.getTeamCategory());
				System.out.println("✅ 배송 담당자 랜덤 배정됨 → " + selected.getUsername());
			} else {
				System.out.println("❌ 배송 담당자 배정 실패 (배송팀 조건 불일치)");
			}

		} catch (Exception e) {
			System.out.println("❌ 예외 발생: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
