package com.dev.HiddenBATHAuto.service.team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.dev.HiddenBATHAuto.model.task.OrderImage;
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

	/**
	 * ✅ 기존 메서드(유지): productionFilter(IN_PROGRESS/DONE/ALL) 기반 조회
	 */
	public Page<Order> getProductionOrdersByDateTypeAndProductionFilter(List<OrderStatus> statuses, Long categoryId,
			String dateType, String productionFilter, LocalDateTime start, LocalDateTime end, Pageable pageable) {
		boolean useCreated = "created".equalsIgnoreCase(dateType);
		String pf = (productionFilter == null || productionFilter.isBlank()) ? "IN_PROGRESS" : productionFilter;

		boolean hasSort = pageable != null && pageable.getSort() != null && pageable.getSort().isSorted();

		Page<Order> page;
		if (useCreated) {
			// created 기준
			page = hasSort
					? orderRepository.findProductionListByCreatedRangeSortable(statuses, categoryId, pf, start, end,
							pageable)
					: orderRepository.findProductionListByCreatedRange(statuses, categoryId, pf, start, end, pageable);
		} else {
			// preferred 기준
			page = hasSort
					? orderRepository.findProductionListByPreferredRangeSortable(statuses, categoryId, pf, start, end,
							pageable)
					: orderRepository.findProductionListByPreferredRange(statuses, categoryId, pf, start, end,
							pageable);
		}

		applySingleLineOptionSummary(page);

		return page;
	}

	/**
	 * ✅ 신규 추가(에러 해결 핵심): 컨트롤러에서 호출하는 메서드 시그니처와 동일합니다.
	 *
	 * - statusFilter가 null이면 ALL (전체) - statusFilter가 있으면 해당 상태만 -
	 * dateType(created/preferred)에 따라 createdAt or preferredDeliveryDate 기준 기간 필터
	 * 적용
	 *
	 * ⚠️ 이 메서드를 사용하려면 OrderRepository에
	 * findProductionListByCreatedRangeStatusSortable /
	 * findProductionListByPreferredRangeStatusSortable 메서드가 존재해야 합니다(아래 2)에서 전체 코드
	 * 제공).
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilter(Long categoryId, String dateType,
			OrderStatus statusFilter, // null이면 ALL
			LocalDateTime start, LocalDateTime end, Pageable pageable) {

		boolean useCreated = "created".equalsIgnoreCase(dateType);
		boolean allStatus = (statusFilter == null);

		// 정렬 유무와 상관없이 "Sort가 Pageable에 들어오면 자동 반영"되는 쿼리만 쓰도록 구성
		// (Query 내부에 ORDER BY를 넣지 않는 방식)
		Page<Order> page;
		if (useCreated) {
			page = orderRepository.findProductionListByCreatedRangeStatusSortable(categoryId, allStatus, statusFilter,
					start, end, pageable);
		} else {
			page = orderRepository.findProductionListByPreferredRangeStatusSortable(categoryId, allStatus, statusFilter,
					start, end, pageable);
		}

		applySingleLineOptionSummary(page);

		return page;
	}

	// ✅ 옵션 한줄 요약 세팅(공통화)
	private void applySingleLineOptionSummary(Page<Order> page) {
		if (page == null || page.getContent() == null)
			return;

		for (Order o : page.getContent()) {
			OrderItem item = o.getOrderItem();
			if (item == null)
				continue;
			item.setFormattedOptionText(buildOptionSummarySingleLine(o, item));
		}
	}

	/**
	 * "카테고리 / 제품명 / 사이즈 / 색상" 처럼 줄바꿈 없이 한 줄로 생성
	 */
	private String buildOptionSummarySingleLine(Order order, OrderItem item) {

		// 1) optionJson 파싱
		Map<String, Object> map = parseJsonToMap(item.getOptionJson());

		// 2) 우선순위대로 값 뽑기
		String category = safeText(
				(order != null && order.getProductCategory() != null) ? order.getProductCategory().getName() : null);

		String productName = safeText(item.getProductName());

		String size = pickFirstValue(map, List.of("사이즈", "size", "Size", "옵션_사이즈", "옵션사이즈"));
		String color = pickFirstValue(map, List.of("색상", "color", "Color", "컬러", "옵션_색상", "옵션색상"));
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
			tokens.add(type);

		// 4) 슬래시 구분
		return String.join(" / ", tokens);
	}

	private Map<String, Object> parseJsonToMap(String json) {
		if (json == null || json.isBlank())
			return Collections.emptyMap();
		try {
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
		s = s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
		s = s.replaceAll("\\s{2,}", " ").trim();
		return s;
	}

	// ===== 아래 기존 메서드들(원본 그대로 유지) =====

	public Page<Order> getProductionOrdersByDateType(List<OrderStatus> statuses, Long categoryId, String dateType,
			LocalDateTime start, LocalDateTime end, Pageable pageable) {

		if ("created".equalsIgnoreCase(dateType)) {
			return orderRepository.findByCreatedDateRangeFlexible(statuses, categoryId, start, end, pageable);
		} else {
			return orderRepository.findByPreferredDateRangeFlexible(statuses, categoryId, start, end, pageable);
		}
	}

	public Page<Order> getProductionOrders(List<OrderStatus> statuses, Long categoryId, LocalDate preferredDate,
			Pageable pageable) {

		LocalDateTime startOfDay = preferredDate.atStartOfDay();
		LocalDateTime endOfDay = preferredDate.plusDays(1).atStartOfDay();

		return orderRepository.findFilteredOrders(statuses, categoryId, startOfDay, endOfDay, pageable);
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
				(dateType != null && dateType.equals("requested")) ? "requested" : "processed", start, end, pageable);
	}

	public Page<AsTask> getAsTasks(Member handler, LocalDate asDate, Pageable pageable) {
		LocalDateTime start = asDate.atStartOfDay();
		LocalDateTime end = start.plusDays(1);

		return asTaskRepository.findByAssignedHandlerAndDate(handler.getId(), start, end, pageable);
	}

	@Transactional(readOnly = true)
	public List<StickerPrintDto> getStickerPrintItems(List<Long> orderIds, Long allowedCategoryId) {

		List<Order> orders = orderRepository.findAllForStickerPrint(orderIds);

		if (allowedCategoryId != null) {
			orders = orders.stream().filter(
					o -> o.getProductCategory() != null && allowedCategoryId.equals(o.getProductCategory().getId()))
					.toList();
		}

		List<StickerPrintDto> result = new ArrayList<>();

		for (Order o : orders) {
			OrderItem orderItem = o.getOrderItem();

			// 업체명
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

			boolean standard = o.isStandard();

			// 옵션 JSON 파싱
			Map<String, String> optMap = parseOptionJsonToMap(orderItem != null ? orderItem.getOptionJson() : null);

			// 모델명 규칙
			String modelName = standard ? nvl(optMap.get("제품명")) : nvl(optMap.get("제품"));
			if (isBlank(modelName))
				modelName = "-";

			// ✅✅ 규격일 때 제품코드
			String productCode = "";
			if (standard) {
				productCode = nvl(optMap.get("제품코드")).trim();
				if (isBlank(productCode))
					productCode = ""; // 출력 조건에서 걸러짐
			}

			// 색상 (코드면 한글명 병기)
			String colorRaw = nvl(optMap.get("색상")).trim();
			String colorDisplay = buildColorDisplay(colorRaw);

			// 사이즈 (넓이 숫자 뒤 mm)
			String sizeRaw = nvl(optMap.get("사이즈")).trim();
			String size = buildSizeWithWidthMm(sizeRaw);
			if (isBlank(size))
				size = "-";

			// 옵션여부(4개)
			List<String> optionFlags = new ArrayList<>();
			addOptIfPresent(optionFlags, "티슈위치", optMap);
			addOptIfPresent(optionFlags, "드라이걸이", optMap);
			addOptIfPresent(optionFlags, "콘센트", optMap);
			addOptIfPresent(optionFlags, "LED", optMap);

			// 비고
			String adminMemo = nvl(o.getAdminMemo()).trim();

			// 관리자 업로드 이미지 1장
			String adminImageUrl = "";
			try {
				List<OrderImage> adminImages = (o.getAdminUploadedImages() != null) ? o.getAdminUploadedImages()
						: List.of();
				if (!adminImages.isEmpty()) {
					adminImageUrl = resolveAdminImageUrl(adminImages.get(0));
				}
			} catch (Exception ignore) {
				adminImageUrl = "";
			}

			StickerPrintDto dto = StickerPrintDto.builder().orderId(o.getId()).companyName(companyName)
					.standard(standard).modelName(modelName).productCode(productCode) // ✅ 추가
					.colorDisplay(colorDisplay).size(size).optionFlags(optionFlags).adminMemo(adminMemo)
					.adminImageUrl(adminImageUrl).build();

			result.add(dto);
		}

		result.sort(Comparator.comparingInt(d -> orderIds.indexOf(d.getOrderId())));
		return result;
	}

	private Map<String, String> parseOptionJsonToMap(String optionJson) {
		if (optionJson == null || optionJson.isBlank())
			return new HashMap<>();
		try {
			return objectMapper.readValue(optionJson, new TypeReference<Map<String, String>>() {
			});
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

	private void addOptIfPresent(List<String> out, String key, Map<String, String> map) {
		String v = nvl(map.get(key)).trim();
		if (!isBlank(v))
			out.add(key + ": " + v);
	}

	private String nvl(String v) {
		return v == null ? "" : v;
	}

	private boolean isBlank(String v) {
		return v == null || v.trim().isEmpty();
	}

	/**
	 * 색상 코드 -> "HW (히든 화이트)" 형태로 변환
	 */
	private String buildColorDisplay(String raw) {
		if (isBlank(raw))
			return "-";

		String code = raw.trim();
		int cut = code.indexOf(' ');
		if (cut > 0)
			code = code.substring(0, cut);
		cut = code.indexOf('(');
		if (cut > 0)
			code = code.substring(0, cut);
		code = code.trim().toUpperCase();

		Map<String, String> map = COLOR_MAP();
		if (map.containsKey(code)) {
			return code + " (" + map.get(code) + ")";
		}
		return raw;
	}

	private Map<String, String> COLOR_MAP() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("HW", "히든 화이트");
		m.put("HB", "히든 블랙");
		m.put("HC", "히든 크림");
		m.put("HG", "히든 그레이");
		m.put("G", "골드");
		m.put("S", "실버");
		m.put("IV", "아이보리");
		m.put("HN", "히든 네츄럴");
		m.put("DB", "다크블루");
		m.put("LW", "라이트 우드");
		m.put("MG", "미스트 그레이");
		m.put("GB", "그레이쉬 브라운");
		m.put("SP", "소프트 핑크");
		m.put("SB", "소프트 블루");
		return m;
	}

	/**
	 * 사이즈 문자열 정규화 + mm 자동 부착
	 */
	private String buildSizeWithWidthMm(String sizeRaw) {
		if (isBlank(sizeRaw))
			return sizeRaw;

		String s = sizeRaw;

		s = s.replaceAll("넓이(?!\\s*\\(W\\))", "넓이(W)");
		s = s.replaceAll("높이(?!\\s*\\(H\\))", "높이(H)");
		s = s.replaceAll("깊이(?!\\s*\\(D\\))", "깊이(D)");

		Pattern p = Pattern.compile("((?:넓이\\(W\\)|높이\\(H\\)|깊이\\(D\\))\\s*:\\s*)(\\d+)(?!\\s*mm)",
				Pattern.CASE_INSENSITIVE);

		Matcher m = p.matcher(s);
		s = m.replaceAll("$1$2mm");

		return s;
	}

	/**
	 * ⚠️ OrderImage 실제 URL 필드명에 맞춰 한 줄만 교체하세요.
	 */
	private String resolveAdminImageUrl(OrderImage img) {
		if (img == null)
			return "";
		String url = img.getUrl(); // TODO: 실제 필드명 맞으면 그대로 사용
		return url == null ? "" : url.trim();
	}
}