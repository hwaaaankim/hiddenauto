package com.dev.HiddenBATHAuto.service.team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.production.StickerPrintDto;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamTaskService {

	private final OrderRepository orderRepository;
	private final AsTaskRepository asTaskRepository;
	private final ObjectMapper objectMapper;

	public Page<Order> getProductionOrdersByDateTypeAndProductionFilter(List<OrderStatus> statuses, Long categoryId,
			String dateType, String productionFilter, LocalDateTime start, LocalDateTime end, Pageable pageable) {
		boolean useCreated = "created".equalsIgnoreCase(dateType);
		String pf = (productionFilter == null || productionFilter.isBlank()) ? "IN_PROGRESS" : productionFilter;

		Page<Order> page = useCreated
				? orderRepository.findProductionListByCreatedRange(statuses, categoryId, pf, start, end, pageable)
				: orderRepository.findProductionListByPreferredRange(statuses, categoryId, pf, start, end, pageable);

		// ✅ 옵션 한줄 요약 세팅
		for (Order o : page.getContent()) {
			OrderItem item = o.getOrderItem();
			if (item == null)
				continue;

			// 원하신 순서: 카테고리/제품명/사이즈/색상 ...
			item.setFormattedOptionText(buildOptionSummarySingleLine(o, item));
		}

		return page;
	}

	/**
	 * "카테고리 / 제품명 / 사이즈 / 색상" 처럼 줄바꿈 없이 한 줄로 생성
	 */
	private String buildOptionSummarySingleLine(Order order, OrderItem item) {

		// 1) optionJson 파싱
		Map<String, Object> map = parseJsonToMap(item.getOptionJson());

		// 2) 우선순위대로 값 뽑기 (키는 프로젝트마다 다를 수 있어서 후보키 목록 제공)
		String category = safeText(
				// order에 이미 productCategory(TeamCategory)가 있으니 그걸 우선 사용
				(order != null && order.getProductCategory() != null) ? order.getProductCategory().getName() : null);

		// 제품명은 OrderItem.productName을 우선
		String productName = safeText(item.getProductName());

		String size = pickFirstValue(map, List.of("사이즈", "size", "Size", "옵션_사이즈", "옵션사이즈"));
		String color = pickFirstValue(map, List.of("색상", "color", "Color", "컬러", "옵션_색상", "옵션색상"));

		// 필요 시 추가 확장(예: 재질/타입/마감/옵션명 등)
		String type = pickFirstValue(map, List.of("타입", "type", "Type", "옵션", "option"));

		// 3) 출력용 토큰 구성 (값 있는 것만)
		List<String> tokens = new ArrayList<>();
		if (!category.isBlank())
			tokens.add(category);
		if (!productName.isBlank())
			tokens.add(productName);
		if (!size.isBlank())
			tokens.add("사이즈:" + size);
		if (!color.isBlank())
			tokens.add("색상:" + color);
		if (!type.isBlank())
			tokens.add(type); // 라벨 원하시면 "타입:" 붙이셔도 됩니다.

		// 4) 슬래시 구분으로 한 줄 생성
		return String.join(" / ", tokens);
	}

	private Map<String, Object> parseJsonToMap(String json) {
		if (json == null || json.isBlank())
			return Collections.emptyMap();
		try {
			// 순서 유지 위해 LinkedHashMap
			return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
			});
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private String pickFirstValue(Map<String, Object> map, List<String> keys) {
		if (map == null || map.isEmpty() || keys == null)
			return "";
		for (String k : keys) {
			if (k == null)
				continue;
			Object v = map.get(k);
			String s = safeText(v);
			if (!s.isBlank())
				return s;
		}
		return "";
	}

	private String safeText(Object v) {
		if (v == null)
			return "";
		String s = String.valueOf(v);

		// 줄바꿈/탭 제거 + 다중 공백 정리
		s = s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
		s = s.replaceAll("\\s{2,}", " ").trim();

		return s;
	}

	public Page<Order> getProductionOrdersByDateType(List<OrderStatus> statuses, Long categoryId, String dateType,
			LocalDateTime start, LocalDateTime end, Pageable pageable) {

		if ("created".equalsIgnoreCase(dateType)) {
			return orderRepository.findByCreatedDateRangeFlexible(statuses, categoryId, start, end, pageable);
		} else {
			// 기본값은 preferred (배송희망일)
			return orderRepository.findByPreferredDateRangeFlexible(statuses, categoryId, start, end, pageable);
		}
	}

	public Page<Order> getProductionOrders(List<OrderStatus> statuses, Long categoryId, LocalDate preferredDate,
			Pageable pageable) {

		LocalDateTime startOfDay = preferredDate.atStartOfDay();
		LocalDateTime endOfDay = preferredDate.plusDays(1).atStartOfDay();

		Page<Order> result = orderRepository.findFilteredOrders(statuses, categoryId, startOfDay, endOfDay, pageable);
		return result;
	}

	public Page<Order> getDeliveryOrders(Member member, LocalDate preferredDate, Pageable pageable) {
		if (!"배송팀".equals(member.getTeam().getName()))
			throw new AccessDeniedException("접근 불가");

		return orderRepository.findDeliveryOrders(List.of(OrderStatus.PRODUCTION_DONE, OrderStatus.DELIVERY_DONE),
				member.getId(), preferredDate, pageable);
	}

	public Page<AsTask> getFilteredAsTasks(Member handler, AsStatus status, String dateType, LocalDate baseDate,
			Pageable pageable) {
		LocalDateTime start = (baseDate != null ? baseDate : LocalDate.now()).atStartOfDay();
		LocalDateTime end = start.plusDays(1);

		return asTaskRepository.findAsTasksByFilter(handler.getId(), status,
				(dateType != null && dateType.equals("requested")) ? "requested" : "processed", // default "processed"
				start, end, pageable);
	}

	public Page<AsTask> getAsTasks(Member handler, LocalDate asDate, Pageable pageable) {
		LocalDateTime start = asDate.atStartOfDay();
		LocalDateTime end = start.plusDays(1);

		return asTaskRepository.findByAssignedHandlerAndDate(handler.getId(), start, end, pageable);
	}

	@Transactional(readOnly = true)
	public List<StickerPrintDto> getStickerPrintItems(List<Long> orderIds, Long allowedCategoryId) {

		// ✅ 출력은 "최신 데이터"가 중요하므로 서버에서 다시 조회
		List<Order> orders = orderRepository.findAllForStickerPrint(orderIds);

		// allowedCategoryId가 있으면 해당 카테고리만 남김(하부장 제한)
		if (allowedCategoryId != null) {
			orders = orders.stream().filter(
					o -> o.getProductCategory() != null && allowedCategoryId.equals(o.getProductCategory().getId()))
					.toList();
		}

		List<StickerPrintDto> result = new ArrayList<>();

		for (Order o : orders) {
			OrderItem item = o.getOrderItem();

			String companyName = "-";
			try {
				if (o.getTask() != null && o.getTask().getRequestedBy() != null
						&& o.getTask().getRequestedBy().getCompany() != null) {
					String n = o.getTask().getRequestedBy().getCompany().getCompanyName();
					if (n != null && !n.isBlank())
						companyName = n;
				}
			} catch (Exception ignore) {
				companyName = "-";
			}

			String region = safeJoinWithSpace(safe(o.getDoName()), safe(o.getSiName()), safe(o.getGuName()));

			if (o.getZipCode() != null && !o.getZipCode().isBlank()) {
				region = region + " (" + o.getZipCode() + ")";
			}

			String optionSummary = "";
			if (item != null) {
				// 목록에서 이미 formattedOptionText를 세팅하지만,
				// "출력 시점"에도 안전하게 한번 더 생성(원하시면 그대로 item.getFormattedOptionText()만 써도 됩니다)
				optionSummary = buildOptionSummarySingleLine(o, item);
			}

			StickerPrintDto dto = StickerPrintDto.builder()
				    .orderId(o.getId())
				    .status(o.getStatus() != null ? o.getStatus().name() : "-")
				    .companyName(companyName)
				    .roadAddress(safe(o.getRoadAddress()))
				    .detailAddress(safe(o.getDetailAddress()))
				    .regionText(region != null ? region.trim() : "")
				    .productName(item != null ? safe(item.getProductName()) : "-")
				    .quantity(item != null ? item.getQuantity() : 0)
				    .optionSummary(optionSummary)
				    .preferredDeliveryDate(
				        o.getPreferredDeliveryDate() != null
				            ? o.getPreferredDeliveryDate().toLocalDate()
				            : null
				    )
				    .build();
			result.add(dto);
		}

		// ✅ 출력 순서: 사용자가 체크한 순서대로 하려면 "orderIds 순서"를 보존해야 합니다.
		// 지금은 DB 조회 결과 순서가 보장되지 않으므로, 체크한 ID 순서대로 정렬해줍니다.
		result.sort(Comparator.comparingInt(d -> orderIds.indexOf(d.getOrderId())));

		return result;
	}

	private String safe(String v) {
		return (v == null) ? "" : v.trim();
	}

	private String safeJoinWithSpace(String a, String b, String c) {
		String s = (a + " " + b + " " + c).replaceAll("\\s{2,}", " ").trim();
		return s;
	}
}
