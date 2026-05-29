package com.dev.HiddenBATHAuto.service.order;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.Province;
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
import com.dev.HiddenBATHAuto.repository.auth.CityRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
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
	private final ProvinceRepository provinceRepository;
	private final CityRepository cityRepository;
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final CartRepository cartRepository;

	@Value("${spring.upload.path}")
	private String uploadRootPath;
	private static final String DELIVERY_TEAM_NAME = "배송팀";

	public String createTaskWithOrders(Member member, List<OrderRequestItemDTO> items, int pointUsed)
			throws JsonProcessingException {
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

			Cart cart = cartRepository.findById(dto.getCartId()).orElseThrow(() -> {
				System.out.println("❌ 존재하지 않는 카트 ID: " + dto.getCartId());
				return new IllegalArgumentException("존재하지 않는 카트 ID: " + dto.getCartId());
			});

			Order order = new Order();
			order.setTask(task);

			// ✅ standard 여부를 여기서 확정
			boolean isStandard = cart.isStandard();
			order.setStandard(isStandard);

			int quantity = cart.getQuantity();
			int productCost = cart.getPrice();
			int deliveryPrice = dto.getDeliveryPrice();
			int singleOrderTotal = quantity * productCost + deliveryPrice;
			totalPrice += singleOrderTotal;

			System.out.printf("  - 수량: %d, 제품단가: %d, 배송비: %d, 합계: %d\n", quantity, productCost, deliveryPrice,
					singleOrderTotal);

			DeliveryMethod method = deliveryMethodRepository.findById(dto.getDeliveryMethodId()).orElseThrow(() -> {
				System.out.println("❌ 존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId());
				return new IllegalArgumentException("존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId());
			});

			String ordererName = normalizeRequiredText(dto.getOrdererName(), "주문자 이름");
			String ordererPhone = normalizeRequiredText(dto.getOrdererPhone(), "주문자 연락처");

			order.setQuantity(quantity);
			order.setProductCost(productCost);

			order.setOrdererName(ordererName);
			order.setOrdererPhone(ordererPhone);

			order.setZipCode(dto.getZipCode());
			order.setRoadAddress(dto.getMainAddress());
			order.setDetailAddress(dto.getDetailAddress());
			order.setPreferredDeliveryDate(dto.getPreferredDeliveryDate().atStartOfDay());
			order.setOrderComment(cart.getAdditionalInfo());
			order.setDeliveryMethod(method);

			refineAddressFromFullRoad(order);

			if (isDirectDelivery(method)) {
				assignDeliveryHandlerIfPossible(order);
			} else {
				order.setAssignedDeliveryHandler(null);
				order.setAssignedDeliveryTeam(null);
				System.out.println("🚚 직배송이 아닌 배송수단이므로 배송 담당자를 배정하지 않습니다. 배송수단=" + method.getMethodName());
			}

			Map<String, Object> localizedOptionMap = objectMapper.readValue(cart.getLocalizedOptionJson(),
					new TypeReference<Map<String, Object>>() {
					});

			String categoryName = Optional.ofNullable(localizedOptionMap.get("카테고리")).map(Object::toString)
					.orElseThrow(() -> new IllegalArgumentException("카테고리 정보가 없습니다."));

			if ("거울".equals(categoryName)) {
				String ledOption = Optional.ofNullable(localizedOptionMap.get("LED 추가")).map(Object::toString)
						.orElse("");
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

			// ===== OrderItem 생성 =====
			OrderItem orderItem = new OrderItem();
			orderItem.setOrder(order);
			orderItem.setQuantity(quantity);

			// 기본값 (둘 다 없을 때)
			orderItem.setProductName("제품명없음");

			// ===== 옵션 변환 + 제품명 세팅 =====
			try {
				Map<String, String> localizedMap = OptionTranslator.getLocalizedOptionMap(cart.getLocalizedOptionJson(),
						productSeriesRepository, productRepository, productColorRepository,
						productOptionPositionRepository);

				// ✅ standard=true => "제품명"
				// ✅ standard=false => "제품"
				// ✅ 둘 다 없으면 "제품명없음"
				String productName = extractProductNameByStandard(isStandard, localizedMap);
				orderItem.setProductName(productName);

				String convertedJson = objectMapper.writeValueAsString(localizedMap);
				orderItem.setOptionJson(convertedJson);

			} catch (Exception e) {
				System.out.println("❌ 옵션 변환 실패: " + e.getMessage());
				throw new RuntimeException("옵션 변환 실패", e);
			}

			order.setOrderItem(orderItem);

			// ===== 이미지 복사 =====
			List<OrderImage> orderImages = new ArrayList<>();
			String today = LocalDate.now().toString();
			Long memberId = member.getId();
			String destDir = String.format("order/order/%d/%s/request", memberId, today);
			File destFolder = Paths.get(uploadRootPath, destDir).toFile();
			if (!destFolder.exists())
				destFolder.mkdirs();

			for (CartImage cartImg : cart.getImages()) {
				try {
					Path imagePath = Paths.get(cartImg.getImagePath());
					Path fullSourcePath = imagePath.isAbsolute() ? imagePath
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
		System.out.printf("💰 총금액: %d원, 보유포인트: %d → 잔여포인트: %d, 적립예정: %dP\n", totalPrice, currentPoint, remainingPoint,
				rewardPoint);

		task.setOrders(orderList);
		task.setTotalPrice(totalPrice);
		taskRepository.save(task);

		System.out.println("🎉 발주 저장 완료!");
		return "발주가 완료되었습니다.";
	}

	private String normalizeRequiredText(String value, String label) {
	    if (value == null || value.trim().isBlank()) {
	        throw new IllegalArgumentException(label + "을 입력해주세요.");
	    }
	    return value.trim();
	}

	private boolean isDirectDelivery(DeliveryMethod method) {
	    if (method == null || method.getMethodName() == null) {
	        return false;
	    }

	    return "직배송".equals(method.getMethodName().trim());
	}
	
	/**
	 * standard=true -> "제품명" standard=false -> "제품" 둘 다 없으면 -> "제품명없음"
	 */
	private String extractProductNameByStandard(boolean isStandard, Map<String, String> localizedMap) {
		if (localizedMap == null || localizedMap.isEmpty()) {
			return "제품명없음";
		}

		String key = isStandard ? "제품명" : "제품";
		String value = localizedMap.get(key);

		if (value == null || value.isBlank()) {
			return "제품명없음";
		}
		return value.trim();
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

	/**
	 * 주소 문자열을 토대로 provinceId/cityId/districtId 를 유연하게 해석하고, 포함 매칭(구→시→도) 우선순위로 배송
	 * 담당자를 배정합니다.
	 *
	 * - 구(guName)가 없어도 배정 진행 (도/시만으로도 가능) - "강원도" vs "강원특별자치도" 등 명칭 차이를 정규화하여 동일 도로
	 * 인식
	 */
	private void assignDeliveryHandlerIfPossible(Order order) {
		final String doName = order.getDoName();
		final String siName = order.getSiName();
		final String guName = order.getGuName();

		System.out.println("📦 [주소 파싱 결과]");
		System.out.println("- 도 : " + doName);
		System.out.println("- 시 : " + siName);
		System.out.println("- 구 : " + guName);

		if (doName == null || doName.isBlank()) {
			System.out.println("❌ 도 정보 부족. 배정 중단");
			return;
		}

		try {
			// 1) 도/시/구를 각각 해석하여 키(id) 도출 (구가 없어도 계속 진행)
			RegionKey key = resolveRegionKey(doName, siName, guName);
			if (key.provinceId == null) {
				System.out.println("❌ Province 매칭 실패. 배정 중단");
				return;
			}

			System.out.println("✅ 해석된 RegionKey: provinceId=" + key.provinceId + ", cityId=" + key.cityId
					+ ", districtId=" + key.districtId);

			// 2) 후보 MemberRegion 조회 (배송팀 한정 + 포함 매칭)
			List<MemberRegion> matches = memberRegionRepository.findDeliveryRegionMatches(DELIVERY_TEAM_NAME,
					key.provinceId, key.cityId, key.districtId);
			System.out.println("🔎 포함 매칭 후보(MemberRegion) 수: " + matches.size());

			if (matches.isEmpty()) {
				System.out.println("❌ 배송 담당자 후보 없음");
				return;
			}

			// 3) 후보를 우선순위(구=3, 시=2, 도=1)로 스코어링하여 최상위만 선별
			Map<Member, Integer> bestScopePerMember = new HashMap<>();
			for (MemberRegion mr : matches) {
				Member m = mr.getMember();
				int scope = scopeScore(mr); // district=3, city=2, province=1
				bestScopePerMember.merge(m, scope, Math::max);
			}

			int topScope = bestScopePerMember.values().stream().mapToInt(i -> i).max().orElse(1);
			List<Member> topCandidates = bestScopePerMember.entrySet().stream().filter(e -> e.getValue() == topScope)
					.map(Map.Entry::getKey).collect(Collectors.toList());

			System.out.println("🏅 최고 우선순위: " + topScope + " → 후보 수: " + topCandidates.size());
			if (topCandidates.isEmpty()) {
				System.out.println("❌ 최고 우선순위 후보 없음");
				return;
			}

			// 4) 동순위 다수면 랜덤(원하시면 라운드로빈/최소작업 우선 등으로 교체 가능)
			Member selected = topCandidates.get((int) (Math.random() * topCandidates.size()));
			order.setAssignedDeliveryHandler(selected);
			order.setAssignedDeliveryTeam(selected.getTeamCategory());

			System.out.println("✅ 배송 담당자 배정 완료 → " + selected.getUsername() + " (scope=" + topScope + ")");

		} catch (Exception e) {
			System.out.println("❌ 예외 발생: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** 구(3) > 시(2) > 도(1) 점수 */
	private int scopeScore(MemberRegion mr) {
		if (mr.getDistrict() != null)
			return 3;
		if (mr.getCity() != null)
			return 2;
		return 1; // province-only
	}

	// =========================
	// 헬퍼 메서드
	// =========================

	/** 도/시/구 명칭을 정규화(접미사 제거)하여 비교할 '베이스명'으로 변환 */
	private String normalizeBase(String s) {
		if (s == null)
			return null;
		String trimmed = s.trim();
		// 흔한 접미사 제거: 도/특별자치도/광역시/특별시/자치시/시/군/구 (뒤에서부터 최대 1회)
		String[] suffixes = { "특별자치도", "광역시", "특별시", "자치시", "자치구", "자치군", "도", "시", "군", "구" };
		for (String suf : suffixes) {
			if (trimmed.endsWith(suf)) {
				trimmed = trimmed.substring(0, trimmed.length() - suf.length());
				break;
			}
		}
		return trimmed;
	}

	/** Province/City/District ID를 유연하게 해석 (구가 없어도 OK) */
	private RegionKey resolveRegionKey(String doName, String siName, String guName) {
		String pBase = normalizeBase(doName);
		String cBase = siName != null ? normalizeBase(siName) : null;
		String dBase = guName != null ? normalizeBase(guName) : null;

		// 1) Province 찾기: 이름에 base 포함(양방향 contains)로 완화
		List<Province> provinces = provinceRepository.findAll();
		Province province = pickByBase(provinces, Province::getName, pBase);

		if (province == null) {
			// 강원특별자치도/제주특별자치도 같은 케이스 더 보수적으로 재시도
			// (예: 입력이 "강원도"이고 DB가 "강원특별자치도"인 경우/그 반대)
			province = pickByRelaxed(provinces, Province::getName, pBase);
		}

		Long provinceId = (province != null ? province.getId() : null);
		if (provinceId == null) {
			return new RegionKey(null, null, null);
		}

		// 2) City 찾기 (선택)
		Long cityId = null;
		City city = null;
		if (cBase != null && !cBase.isBlank()) {
			List<City> cities = cityRepository.findByProvinceId(provinceId);
			city = pickByBase(cities, City::getName, cBase);
			if (city == null) {
				city = pickByRelaxed(cities, City::getName, cBase);
			}
			cityId = (city != null ? city.getId() : null);
		}

		// 3) District 찾기 (선택)
		Long districtId = null;
		if (dBase != null && !dBase.isBlank()) {
			List<District> districts;
			if (cityId != null) {
				districts = districtRepository.findByCityId(cityId);
			} else {
				// 서울/세종처럼 City 없이 District가 직접 Province에 매달린 케이스
				districts = districtRepository.findByProvinceId(provinceId);
			}
			District dist = pickByBase(districts, District::getName, dBase);
			if (dist == null) {
				dist = pickByRelaxed(districts, District::getName, dBase);
			}
			districtId = (dist != null ? dist.getId() : null);
		}

		return new RegionKey(provinceId, cityId, districtId);
	}

	/** 베이스명 비교: normalize 후 (A contains B) OR (B contains A) */
	private <T> T pickByBase(List<T> list, java.util.function.Function<T, String> nameFn, String base) {
		if (base == null || base.isBlank())
			return null;
		String b = normalizeBase(base);
		for (T t : list) {
			String n = nameFn.apply(t);
			String nb = normalizeBase(n);
			if (nb != null && (nb.contains(b) || b.contains(nb))) {
				return t;
			}
		}
		return null;
	}

	/** 완화된 비교: 공백 제거/한글 자모 구분 최소화 등을 추가 여지 (지금은 toString contains 로 재시도) */
	private <T> T pickByRelaxed(List<T> list, java.util.function.Function<T, String> nameFn, String keyword) {
		if (keyword == null || keyword.isBlank())
			return null;
		String k = keyword.replaceAll("\\s+", "");
		for (T t : list) {
			String n = nameFn.apply(t);
			if (n == null)
				continue;
			String nn = n.replaceAll("\\s+", "");
			if (nn.contains(k) || k.contains(nn)) {
				return t;
			}
		}
		return null;
	}

	/** provinceId / cityId / districtId 묶음 */
	private record RegionKey(Long provinceId, Long cityId, Long districtId) {
	}
}
