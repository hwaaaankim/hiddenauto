package com.dev.HiddenBATHAuto.service.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.dev.HiddenBATHAuto.model.task.Order;
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
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.utils.OptionTranslator;
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

	public String createTaskWithOrders(Member member, List<OrderRequestItemDTO> items, int pointUsed) {
		Task task = new Task();
		task.setRequestedBy(member);
		task.setStatus(TaskStatus.REQUESTED);
		task.setCustomerNote("임시 고객 메모");
		task.setInternalNote("임시 내부 메모");

		List<Order> orderList = new ArrayList<>();
		int totalPrice = 0;

		for (OrderRequestItemDTO dto : items) {
			Order order = new Order();
			order.setTask(task);

			int quantity = dto.getQuantity();
			int productCost = dto.getPrice();
			int deliveryPrice = dto.getDeliveryPrice();
			int singleOrderTotal = quantity * productCost + deliveryPrice;
			totalPrice += singleOrderTotal;

			order.setQuantity(quantity);
			order.setProductCost(productCost);
			order.setZipCode(dto.getZipCode());
			order.setRoadAddress(dto.getMainAddress());
			order.setDetailAddress(dto.getDetailAddress());
			order.setPreferredDeliveryDate(dto.getPreferredDeliveryDate().atStartOfDay());
			refineAddressFromFullRoad(order);
			assignDeliveryHandlerIfPossible(order); // 파싱한 주소로 배송 담당자 배정

			DeliveryMethod method = deliveryMethodRepository.findById(dto.getDeliveryMethodId())
					.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId()));
			order.setDeliveryMethod(method);
			
			String categoryName = dto.getOptionJson().get("카테고리").toString();

			// ✅ "거울" + LED 여부 → 팀카테고리명 분기
			if ("거울".equals(categoryName)) {
			    String ledOption = Optional.ofNullable(dto.getOptionJson().get("LED 추가"))
			                               .map(Object::toString)
			                               .orElse("");
			    System.out.println(ledOption);
			    categoryName = "add".equals(ledOption) ? "LED거울" : "거울";
			}

			// ✅ 팀카테고리 조회 or '배정없음' 처리
			TeamCategory productCategory = teamCategoryRepository.findByName(categoryName)
			    .orElseGet(() -> teamCategoryRepository.findByName("배정없음")
			        .orElseThrow(() -> new IllegalStateException("기본 TeamCategory '배정없음'이 DB에 없습니다.")));

			order.setProductCategory(productCategory);
			order.setStatus(OrderStatus.REQUESTED);

			OrderItem orderItem = new OrderItem();
			orderItem.setOrder(order);
			orderItem.setProductName("임시 제품명");
			orderItem.setQuantity(quantity);

			try {
				Map<String, String> localizedMap = OptionTranslator.getLocalizedOptionMap(
						objectMapper.writeValueAsString(dto.getOptionJson()), productSeriesRepository,
						productRepository, productColorRepository, productOptionPositionRepository);
				String convertedJson = objectMapper.writeValueAsString(localizedMap);
				orderItem.setOptionJson(convertedJson);
			} catch (Exception e) {
				throw new RuntimeException("옵션 변환 실패", e);
			}

			order.setOrderItem(orderItem);
			orderList.add(order);
		}

		Company company = companyRepository.findById(member.getCompany().getId())
				.orElseThrow(() -> new IllegalArgumentException("회사 정보를 찾을 수 없습니다."));

		int currentPoint = company.getPoint();
		int remainingPoint = currentPoint - pointUsed;
		if (remainingPoint < 0) {
			throw new IllegalStateException("사용 가능한 포인트가 부족합니다. 현재 보유: " + currentPoint + "P");
		}

		int rewardPoint = (int) (totalPrice * 0.01);
		company.setPoint(remainingPoint + rewardPoint); // ✅ 변경 감지됨

		task.setOrders(orderList);
		task.setTotalPrice(totalPrice);
		taskRepository.save(task);

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

			for (MemberRegion region : matchedRegions) {
				Member m = region.getMember();
				String teamName = m.getTeam() != null ? m.getTeam().getName() : "null";

				System.out.println("➡️ 후보자: " + m.getUsername() + ", 팀: " + teamName);

				if (m.getRole() == MemberRole.INTERNAL_EMPLOYEE && "배송팀".equals(teamName)) {
					order.setAssignedDeliveryHandler(m);
					order.setAssignedDeliveryTeam(m.getTeamCategory());

					System.out.println("✅ 배송 담당자 배정됨 → " + m.getUsername());
					return;
				}
			}

			System.out.println("❌ 배송 담당자 배정 실패 (배송팀 조건 불일치)");

		} catch (Exception e) {
			System.out.println("❌ 예외 발생: " + e.getMessage());
			e.printStackTrace();
		}
	}

}
