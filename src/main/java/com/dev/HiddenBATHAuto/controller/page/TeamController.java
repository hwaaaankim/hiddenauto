package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.dto.as.TeamAsDetailModalResponse;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryExcelRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryHandlerBulkChangeRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryHandlerChangeRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryOrderSummaryRes;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryReorderByTaskRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryReorderByTaskResponse;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPageResponse;
import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.dto.production.ProductionCheckViewDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionListExcelRowDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionListOutputOptions;
import com.dev.HiddenBATHAuto.dto.production.ProductionOrderCheckResponse;
import com.dev.HiddenBATHAuto.dto.production.ProductionSortOrder;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewCompleteResponse;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewFieldDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewImageDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewOrderDto;
import com.dev.HiddenBATHAuto.dto.production.StickerPrintDto;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.order.DeliveryHandlerChangeAuditService;
import com.dev.HiddenBATHAuto.service.order.DeliveryOrderIndexService;
import com.dev.HiddenBATHAuto.service.order.OrderChangeAuditService;
import com.dev.HiddenBATHAuto.service.order.OrderTeamAccessPolicyService;
import com.dev.HiddenBATHAuto.service.order.DeliveryCompletionService;
import com.dev.HiddenBATHAuto.service.production.MaterialCuttingService;
import com.dev.HiddenBATHAuto.service.production.ProductionListExcelService;
import com.dev.HiddenBATHAuto.service.team.TeamTaskService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryExcelService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryOrderSummaryService;
import com.dev.HiddenBATHAuto.utils.DeliveryProductDisplayUtil;
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
@Slf4j
public class TeamController {

	private final TeamTaskService teamTaskService;
	private final TeamCategoryRepository teamCategoryRepository;
	private final OrderRepository orderRepository;
	private final DeliveryOrderIndexService deliveryOrderIndexService;
	private final DeliveryHandlerChangeAuditService deliveryHandlerChangeAuditService;
	private final AsTaskService asTaskService;
	private final AsImageRepository asImageRepository;
	private final ProvinceRepository provinceRepository;
	private final DeliveryCompletionService deliveryCompletionService;
	private final DeliveryOrderSummaryService deliveryOrderSummaryService;
	private final DeliveryExcelService deliveryExcelService;
	private final ProductionListExcelService productionListExcelService;
	private final OrderChangeAuditService orderChangeAuditService;
	private final OrderTeamAccessPolicyService orderTeamAccessPolicyService;

	private final MaterialCuttingService materialCuttingService;
	private final ObjectMapper objectMapper;

	private static final Long AS_TEAM_ID = 4L;

	/*
	 * 거울(재단)은 실제 생산 카테고리가 아니라
	 * tb_order.mirror_cutting_product = true 를 조회하기 위한 가상 필터입니다.
	 * 실제 TeamCategory ID와 충돌하지 않도록 음수 sentinel 값을 사용합니다.
	 */
	private static final Long PRODUCTION_TEAM_ID = 2L;
	private static final Long MIRROR_CUTTING_FILTER_VALUE = -9000001L;
	private static final String MIRROR_CUTTING_FILTER_LABEL = "재단(거울)";

	/*
	 * 기존 재단(거울) 계정/데이터가 남아 있을 수 있어 호환용으로 유지합니다.
	 * 신규 요구사항의 필터 노출 대상은 생산팀 + 팀카테고리 거울 / LED거울 입니다.
	 */
	private static final Long LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_ID = 14L;
	private static final String LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_NAME = "재단(거울)";

	private static final List<String> MIRROR_CUTTING_FILTER_ALLOWED_TEAM_CATEGORY_NAMES = List.of(
			"거울",
			"LED거울",
			LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_NAME
	);

	/*
	 * 실제 TeamCategory 목록에 과거용 재단(거울) 카테고리가 남아 있더라도
	 * 화면 select에는 실제 카테고리로 노출하지 않습니다.
	 * 거울 재단 조회는 반드시 MIRROR_CUTTING_FILTER_VALUE 가상 옵션으로만 처리합니다.
	 */
	private static final List<String> MIRROR_CUTTING_REAL_CATEGORY_NAMES_TO_HIDE = List.of(
			"재단(거울)",
			"거울(재단)"
	);

	private static final List<OrderStatus> PRODUCTION_LIST_VISIBLE_STATUSES = List.of(
			OrderStatus.CONFIRMED,
			OrderStatus.PRODUCTION_DONE,
			OrderStatus.DISPATCH_DONE,
			OrderStatus.DELIVERY_DONE
	);

	private static final List<String> PRODUCTION_REAL_CATEGORY_NAMES = List.of(
			"슬라이드장",
			"상부장",
			"하부장",
			"플랩장",
			"거울",
			"LED거울"
	);

	private static final List<String> PRODUCTION_LIST_ALLOWED_STATUS_FILTERS = List.of(
			"ALL",
			"CONFIRMED",
			"PRODUCTION_DONE",
			"DISPATCH_DONE",
			"DELIVERY_DONE"
	);

	private static final List<String> PRODUCTION_LIST_ALLOWED_SORT_KEYS = List.of(
			"id",
			"productName",
			"productSeries",
			"deliveryDate",
			"checked"
	);

	@GetMapping("/productionList")
	public String getProductionOrders(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) Long productCategoryId,
			@RequestParam(required = false) String orderIdFrom,
			@RequestParam(required = false) String orderIdTo,
			@RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false, defaultValue = "ALL") String standardType,
			@RequestParam(required = false, defaultValue = "preferred") String dateType,
			@RequestParam(required = false, defaultValue = "CONFIRMED") String statusFilter,

			@RequestParam(required = false, defaultValue = "100") int size,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String sortSpec,
			@RequestParam(required = false) String sortKey,
			@RequestParam(required = false) String sortDir,
			Model model) {

		Member member = principal.getMember();
		OrderIdRangeFilter orderIdRange = resolveOrderIdRange(orderIdFrom, orderIdTo, orderId);
		Long orderIdFromFilter = orderIdRange.from();
		Long orderIdToFilter = orderIdRange.to();
        String productNameFilter = normalizeSearchText(productName);
        String normalizedStandardType = normalizeProductionListStandardType(standardType);
        Boolean standardFilter = parseProductionListStandardFilter(normalizedStandardType);

		if (member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
		}

		boolean isCuttingProductionMember = isCuttingProductionMember(member);
		boolean isLegacyMirrorCuttingProductionMember = isLegacyMirrorCuttingProductionMember(member);
		boolean isLowerCabinetProductionMember = isLowerCabinetProductionMember(member);
		boolean canUseMirrorCuttingFilter = canUseMirrorCuttingFilter(member);

		/*
		 * 제품분류 select의 일반 항목은 실제 TeamCategory ID입니다.
		 * 단, 거울(재단)은 실제 카테고리가 아니라 Order.mirrorCuttingProduct = true 조회용 가상 필터입니다.
		 */
		boolean mirrorCuttingFilterValueSelected = isMirrorCuttingFilterValue(productCategoryId);
		boolean hiddenRealMirrorCuttingCategorySelected = isHiddenRealMirrorCuttingCategoryId(productCategoryId);
		boolean mirrorCuttingFilterSelected = mirrorCuttingFilterValueSelected || hiddenRealMirrorCuttingCategorySelected;

		if (mirrorCuttingFilterSelected && !canUseMirrorCuttingFilter) {
			throw new AccessDeniedException("거울(재단) 조회 권한이 없습니다.");
		}

		Long selectedProductCategoryId = mirrorCuttingFilterSelected
				? MIRROR_CUTTING_FILTER_VALUE
				: normalizeProductCategoryIdOrNull(productCategoryId);

		if (selectedProductCategoryId != null
				&& !isMirrorCuttingFilterValue(selectedProductCategoryId)
				&& !isProductionRealCategoryId(selectedProductCategoryId)) {
			throw new AccessDeniedException("생산팀에서는 실제 6개 생산 카테고리만 조회할 수 있습니다.");
		}

		/*
		 * 핵심 조회 분기
		 * - 거울(재단) 선택: productCategory 조건은 걸지 않고 mirrorCuttingProduct = true 만 추가
		 * - 실제 카테고리 선택: 해당 productCategory.id 기준 조회
		 * - 선택 없음: 일반 직원은 자기 팀 카테고리, 재단 계열은 기존 정책 유지
		 */
		Long targetCategoryId;

		if (mirrorCuttingFilterSelected) {
			targetCategoryId = null;
		} else if (selectedProductCategoryId != null) {
			targetCategoryId = selectedProductCategoryId;
		} else if (isCuttingProductionMember || isLegacyMirrorCuttingProductionMember) {
			targetCategoryId = null;
		} else {
			targetCategoryId = member.getTeamCategory() != null ? member.getTeamCategory().getId() : null;
		}

		boolean mirrorCuttingOnly = mirrorCuttingFilterSelected;

		String normalizedDateType = (dateType == null || dateType.isBlank()) ? "preferred" : dateType.trim();

		if (!"preferred".equals(normalizedDateType) && !"created".equals(normalizedDateType)) {
			normalizedDateType = "preferred";
		}

		/*
		 * 날짜 조회 규칙
		 * - startDate = endDate: 해당일 1일 조회
		 * - startDate만 있음: startDate부터 미래 전체 조회
		 * - endDate만 있음: endDate까지 과거 전체 조회
		 * - 둘 다 없음: 전체 기간 조회
		 *
		 * 중요: 사용자가 날짜 input을 비워서 빈 값으로 검색한 경우에도 Spring에서는 null로 바인딩됩니다.
		 * 따라서 여기서 null을 내일 날짜로 다시 채우면 전체 기간 조회가 불가능해지므로 강제 기본 날짜를 넣지 않습니다.
		 */
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			LocalDate temp = startDate;
			startDate = endDate;
			endDate = temp;
		}

		LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
		LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

		if (size != 100 && size != 200 && size != 300 && size != 400 && size != 500) {
			size = 100;
		}

		if (page < 0) {
			page = 0;
		}

		String sf = normalizeProductionListStatusFilter(statusFilter);
		OrderStatus statusEnum = parseProductionListStatusFilter(sf);

		/*
		 * 오더 ID 범위 검색은 상태 필터보다 우선합니다.
		 * 기본 상태가 CONFIRMED인 화면에서도 범위 안의 생산완료(PRODUCTION_DONE) 오더를
		 * 함께 찾을 수 있도록, FROM 또는 TO가 입력되면 생산팀 허용 상태 전체를 조회합니다.
		 */
		if (orderIdFromFilter != null || orderIdToFilter != null) {
			sf = "ALL";
			statusEnum = null;
		}

		/*
		 * 다중 정렬 규칙
		 * - sortSpec 예: id:ASC,productName:DESC
		 * - 배열 순서가 ORDER BY 우선순서입니다.
		 * - 같은 컬럼/같은 방향 화살표를 다시 누르면 해당 조건만 제거됩니다.
		 * - 구버전 단일 sortKey/sortDir URL도 첫 진입 시 자동 변환하여 호환합니다.
		 */
		List<ProductionSortOrder> productionSortOrders = parseProductionSortOrders(sortSpec, sortKey, sortDir);
		String normalizedSortSpec = serializeProductionSortOrders(productionSortOrders);
		boolean hasCustomSort = !productionSortOrders.isEmpty();
		boolean requiresMemberCheckSort = productionSortOrders.stream()
				.anyMatch(order -> "checked".equals(order.key()));

		Pageable pageable = PageRequest.of(
				page,
				size,
				hasCustomSort && !requiresMemberCheckSort
						? buildProductionMultiSort(productionSortOrders, normalizedDateType)
						: Sort.unsorted()
		);
		Page<Order> orderPage;

		if (hasCustomSort && requiresMemberCheckSort) {
			// 체크상태는 로그인 사용자별 계산값이므로 체크 정렬이 포함된 경우에만 서비스 다중 정렬을 사용합니다.
			orderPage = teamTaskService.getProductionOrdersByDateTypeAndStatusFilterMultiSorted(
					targetCategoryId,
					orderIdFromFilter,
					orderIdToFilter,
					productNameFilter,
					standardFilter,
					normalizedDateType,
					statusEnum,
					start,
					end,
					mirrorCuttingOnly,
					member,
					productionSortOrders,
					pageable
			);
		} else if (hasCustomSort) {
			// ID/제품명/중분류/배송일만 사용하면 DB Pageable 다중 정렬로 처리합니다.
			orderPage = teamTaskService.getProductionOrdersByDateTypeAndStatusFilter(
					targetCategoryId,
					orderIdFromFilter,
					orderIdToFilter,
					productNameFilter,
					standardFilter,
					normalizedDateType,
					statusEnum,
					start,
					end,
					mirrorCuttingOnly,
					pageable
			);
		} else {
			// 정렬 조건이 모두 해제되면 기존 최초 조회 순서로 복원합니다.
			orderPage = teamTaskService.getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
					targetCategoryId,
					orderIdFromFilter,
					orderIdToFilter,
					productNameFilter,
					standardFilter,
					normalizedDateType,
					statusEnum,
					start,
					end,
					mirrorCuttingOnly,
					member.getId(),
					false,
					pageable
			);
		}

		/*
		 * 조회권한과 변경권한을 분리합니다.
		 * - 생산팀 구성원은 현재 생산목록에 노출되는 다른 카테고리도 상세조회/개인확인 가능
		 * - 생산완료/관리자요청은 오더별 canOperateProductionOrder 결과로만 허용
		 * - 거울과 LED거울은 OrderTeamAccessPolicyService에서 동일 작업 그룹으로 상호 허용
		 *
		 * 기존처럼 선택된 카테고리 하나로 화면 전체 권한을 계산하면 타 카테고리 조회 시
		 * 상세 UI까지 변경권한과 결합될 수 있으므로 반드시 행 단위로 계산합니다.
		 */
		Map<Long, Boolean> productionOperationAllowedMap = orderPage.getContent().stream()
				.filter(Objects::nonNull)
				.filter(order -> order.getId() != null)
				.collect(Collectors.toMap(
						Order::getId,
						order -> orderTeamAccessPolicyService.canOperateProductionOrder(member, order),
						(left, right) -> left,
						LinkedHashMap::new
				));

		boolean canBulkComplete = productionOperationAllowedMap.values().stream()
				.anyMatch(Boolean.TRUE::equals);

		/*
		 * 자재재단 버튼은 하부장 직원에게만 노출
		 */
		boolean canMaterialCutting = isLowerCabinetProductionMember;

		Map<Long, String> orderCompanyNameMap = new HashMap<>();

		for (Order o : orderPage.getContent()) {
			String companyName = "-";

			try {
				if (o.getTask() != null && o.getTask().getRequestedBy() != null
						&& o.getTask().getRequestedBy().getCompany() != null) {

					String n = o.getTask().getRequestedBy().getCompany().getCompanyName();

					if (n != null && !n.isBlank()) {
						companyName = n;
					}
				}
			} catch (Exception ignore) {
				companyName = "-";
			}

			orderCompanyNameMap.put(o.getId(), companyName);
		}

		Map<Long, List<ProductionOverviewFieldDto>> orderBriefFieldMap = teamTaskService
				.buildProductionOverviewBriefFieldMap(orderPage.getContent());

		List<Long> currentPageOrderIds = orderPage.getContent().stream().map(Order::getId).collect(Collectors.toList());

        Map<Long, ProductionCheckViewDto> productionCheckViewMap = teamTaskService
                .getProductionCheckViewMap(currentPageOrderIds, member);

		List<TeamCategory> productCategories = buildProductionCategorySelectOptions(
				teamCategoryRepository.findByTeamName("생산팀")
		);

		model.addAttribute("canMaterialCutting", canMaterialCutting);
		model.addAttribute("orders", orderPage.getContent());
		model.addAttribute("page", orderPage);

		// 화면 select의 선택값은 사용자가 실제로 선택한 값만 유지합니다.
		// productCategoryId가 null이면 "전체(기본: 내 분류)"가 선택되어 보이고,
		// 실제 조회는 위에서 계산한 targetCategoryId로 계속 수행됩니다.
		model.addAttribute("productCategoryId", selectedProductCategoryId);
		model.addAttribute("targetProductCategoryId", targetCategoryId);
		model.addAttribute("orderIdFrom", orderIdFromFilter);
		model.addAttribute("orderIdTo", orderIdToFilter);
		// 기존 단건 URL/템플릿 호환용입니다. 범위가 단건일 때만 값을 제공합니다.
		model.addAttribute("orderId", Objects.equals(orderIdFromFilter, orderIdToFilter) ? orderIdFromFilter : null);
        model.addAttribute("productName", productNameFilter);
        model.addAttribute("standardType", normalizedStandardType);
		model.addAttribute("dateType", normalizedDateType);
		model.addAttribute("statusFilter", sf);

		model.addAttribute("size", size);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("productionDateRangeLabel", buildProductionDateRangeLabel(startDate, endDate));
		model.addAttribute("productCategories", productCategories);

		model.addAttribute("canBulkComplete", canBulkComplete);
		model.addAttribute("canChangeProductionStatus", canBulkComplete);
		model.addAttribute("productionOperationAllowedMap", productionOperationAllowedMap);
		model.addAttribute("isCuttingProductionMember", isCuttingProductionMember);
		model.addAttribute("isMirrorCuttingProductionMember", isLegacyMirrorCuttingProductionMember);
		model.addAttribute("canUseMirrorCuttingFilter", canUseMirrorCuttingFilter);
		model.addAttribute("mirrorCuttingFilterValue", MIRROR_CUTTING_FILTER_VALUE);
		model.addAttribute("mirrorCuttingFilterLabel", MIRROR_CUTTING_FILTER_LABEL);
		model.addAttribute("isMirrorCuttingFilterSelected", mirrorCuttingFilterSelected);

		model.addAttribute("orderCompanyNameMap", orderCompanyNameMap);

		model.addAttribute("orderBriefFieldMap", orderBriefFieldMap);
		model.addAttribute("currentPageOrderIds", currentPageOrderIds);
        model.addAttribute("productionCheckViewMap", productionCheckViewMap);
		model.addAttribute("cuttingEligibilityMap",
				materialCuttingService.buildCuttingEligibilityMap(orderPage.getContent()));

		model.addAttribute("sortSpec", normalizedSortSpec);
		model.addAttribute("sortOrders", productionSortOrders);
		// 구버전 템플릿/링크 호환용으로 첫 번째 조건도 함께 제공합니다.
		model.addAttribute("sortKey", productionSortOrders.isEmpty() ? "" : productionSortOrders.get(0).key());
		model.addAttribute("sortDir", productionSortOrders.isEmpty() ? "" : productionSortOrders.get(0).directionName());

		return "administration/team/production/productionList";
	}

	private List<ProductionSortOrder> parseProductionSortOrders(
			String sortSpec,
			String legacySortKey,
			String legacySortDir
	) {
		LinkedHashMap<String, ProductionSortOrder> ordered = new LinkedHashMap<>();

		if (StringUtils.hasText(sortSpec)) {
			for (String token : sortSpec.split(",")) {
				if (!StringUtils.hasText(token)) {
					continue;
				}

				String[] parts = token.trim().split(":", 2);
				String key = normalizeProductionSortKey(parts[0]);
				String direction = parts.length > 1 ? normalizeProductionSortDirection(parts[1]) : null;

				if (key == null || direction == null) {
					continue;
				}

				ordered.put(key, ProductionSortOrder.of(key, direction));
			}
		}

		if (ordered.isEmpty() && StringUtils.hasText(legacySortKey)) {
			String key = normalizeProductionSortKey(legacySortKey);
			String direction = normalizeProductionSortDirection(legacySortDir);

			if (key != null) {
				ordered.put(key, ProductionSortOrder.of(key, direction != null ? direction : "ASC"));
			}
		}

		return new ArrayList<>(ordered.values());
	}

	private String serializeProductionSortOrders(List<ProductionSortOrder> sortOrders) {
		if (sortOrders == null || sortOrders.isEmpty()) {
			return "";
		}

		return sortOrders.stream()
				.filter(Objects::nonNull)
				.map(order -> order.key() + ":" + order.directionName())
				.collect(Collectors.joining(","));
	}

	private String normalizeProductionSortKey(String rawKey) {
		if (!StringUtils.hasText(rawKey)) {
			return null;
		}

		String candidate = rawKey.trim();

		for (String allowedKey : PRODUCTION_LIST_ALLOWED_SORT_KEYS) {
			if (allowedKey.equalsIgnoreCase(candidate)) {
				return allowedKey;
			}
		}

		// 기존 orderId 키도 id로 호환합니다.
		return "orderId".equalsIgnoreCase(candidate) ? "id" : null;
	}

	private String normalizeProductionSortDirection(String rawDirection) {
		if (!StringUtils.hasText(rawDirection)) {
			return null;
		}

		String direction = rawDirection.trim().toUpperCase(Locale.ROOT);
		return "ASC".equals(direction) || "DESC".equals(direction) ? direction : null;
	}

	private String buildProductionDateRangeLabel(LocalDate startDate, LocalDate endDate) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startDate == null && endDate == null) {
			return "전체 기간";
		}

		if (startDate != null && endDate != null) {
			if (startDate.equals(endDate)) {
				return startDate.format(formatter);
			}

			return startDate.format(formatter) + " ~ " + endDate.format(formatter);
		}

		if (startDate != null) {
			return startDate.format(formatter) + " ~";
		}

		return "~ " + endDate.format(formatter);
	}

	private String normalizeProductionListStandardType(String rawStandardType) {
		if (!StringUtils.hasText(rawStandardType)) {
			return "ALL";
		}

		String normalized = rawStandardType.trim().toUpperCase(Locale.ROOT);

		return switch (normalized) {
		case "STANDARD", "TRUE" -> "STANDARD";
		case "NON_STANDARD", "NONSTANDARD", "FALSE" -> "NON_STANDARD";
		default -> "ALL";
		};
	}

	private Boolean parseProductionListStandardFilter(String normalizedStandardType) {
		if ("STANDARD".equals(normalizedStandardType)) {
			return Boolean.TRUE;
		}

		if ("NON_STANDARD".equals(normalizedStandardType)) {
			return Boolean.FALSE;
		}

		return null;
	}

	private String normalizeProductionListStatusFilter(String rawStatusFilter) {
		if (!StringUtils.hasText(rawStatusFilter)) {
			return "CONFIRMED";
		}

		String normalized = rawStatusFilter.trim().toUpperCase(Locale.ROOT);

		if (!PRODUCTION_LIST_ALLOWED_STATUS_FILTERS.contains(normalized)) {
			return "CONFIRMED";
		}

		return normalized;
	}

	private OrderStatus parseProductionListStatusFilter(String normalizedStatusFilter) {
		if (!StringUtils.hasText(normalizedStatusFilter) || "ALL".equals(normalizedStatusFilter)) {
			return null;
		}

		try {
			OrderStatus parsed = OrderStatus.valueOf(normalizedStatusFilter);

			if (!isProductionListVisibleStatus(parsed)) {
				return OrderStatus.CONFIRMED;
			}

			return parsed;

		} catch (IllegalArgumentException e) {
			return OrderStatus.CONFIRMED;
		}
	}

	private boolean isProductionListVisibleStatus(OrderStatus status) {
		return status != null && PRODUCTION_LIST_VISIBLE_STATUSES.contains(status);
	}
	
	private String normalizeSearchText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

	private record OrderIdRangeFilter(Long from, Long to) {
	}

	private OrderIdRangeFilter resolveOrderIdRange(
			String orderIdFrom,
			String orderIdTo,
			String legacyOrderId
	) {
		boolean hasExplicitRange = StringUtils.hasText(orderIdFrom) || StringUtils.hasText(orderIdTo);

		Long from = parsePositiveLongFilter(orderIdFrom, "오더 ID FROM");
		Long to = parsePositiveLongFilter(orderIdTo, "오더 ID TO");

		if (!hasExplicitRange) {
			Long legacy = parsePositiveLongFilter(legacyOrderId, "오더 ID");
			if (legacy != null) {
				from = legacy;
				to = legacy;
			}
		}

		if (from != null && to != null && from > to) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"오더 ID TO는 FROM보다 크거나 같아야 합니다. 단건 조회는 FROM과 TO를 같은 값으로 입력해 주세요."
			);
		}

		return new OrderIdRangeFilter(from, to);
	}

	private Long parsePositiveLongFilter(String value, String fieldLabel) {
		if (!StringUtils.hasText(value)) {
			return null;
		}

		try {
			long parsed = Long.parseLong(value.trim());
			if (parsed <= 0) {
				throw new NumberFormatException("positive value required");
			}
			return parsed;
		} catch (NumberFormatException e) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					fieldLabel + "는 1 이상의 정수로 입력해 주세요."
			);
		}
	}

	private boolean isCuttingProductionMember(Member member) {
		if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			return false;
		}

		if (member.getTeamCategory() == null) {
			return false;
		}

		String categoryName = member.getTeamCategory().getName();

		return "재단".equals(categoryName) || isLegacyMirrorCuttingProductionMember(member);
	}

	private boolean isLegacyMirrorCuttingProductionMember(Member member) {
		if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			return false;
		}

		if (member.getTeamCategory() == null) {
			return false;
		}

		Long categoryId = member.getTeamCategory().getId();
		String categoryName = member.getTeamCategory().getName();

		return Objects.equals(LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_ID, categoryId)
				&& LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_NAME.equals(categoryName);
	}

	private List<TeamCategory> buildProductionCategorySelectOptions(List<TeamCategory> source) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}

		return source.stream()
				.filter(Objects::nonNull)
				.filter(this::isProductionRealCategory)
				.collect(Collectors.toList());
	}

	private boolean isProductionRealCategory(TeamCategory category) {
		if (category == null || category.getTeam() == null || category.getName() == null) {
			return false;
		}
		return Objects.equals(PRODUCTION_TEAM_ID, category.getTeam().getId())
				&& PRODUCTION_REAL_CATEGORY_NAMES.contains(category.getName().trim());
	}

	private boolean isProductionRealCategoryId(Long categoryId) {
		if (categoryId == null) {
			return false;
		}
		return teamCategoryRepository.findById(categoryId)
				.map(this::isProductionRealCategory)
				.orElse(false);
	}

	private boolean isHiddenRealMirrorCuttingCategory(TeamCategory category) {
		if (category == null) {
			return false;
		}

		if (Objects.equals(LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_ID, category.getId())) {
			return true;
		}

		String categoryName = category.getName();

		if (categoryName == null) {
			return false;
		}

		return MIRROR_CUTTING_REAL_CATEGORY_NAMES_TO_HIDE.contains(categoryName.trim());
	}

	private boolean isHiddenRealMirrorCuttingCategoryId(Long productCategoryId) {
		if (productCategoryId == null) {
			return false;
		}

		if (Objects.equals(LEGACY_MIRROR_CUTTING_TEAM_CATEGORY_ID, productCategoryId)) {
			return true;
		}

		return teamCategoryRepository.findById(productCategoryId)
				.map(this::isHiddenRealMirrorCuttingCategory)
				.orElse(false);
	}

	private boolean canUseMirrorCuttingFilter(Member member) {
		if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			return false;
		}

		if (member.getTeamCategory() == null || member.getTeamCategory().getName() == null) {
			return false;
		}

		String categoryName = member.getTeamCategory().getName().trim();

		return MIRROR_CUTTING_FILTER_ALLOWED_TEAM_CATEGORY_NAMES.contains(categoryName);
	}

	private boolean isMirrorCuttingFilterValue(Long productCategoryId) {
		return Objects.equals(MIRROR_CUTTING_FILTER_VALUE, productCategoryId);
	}

	private Long normalizeProductCategoryIdOrNull(Long productCategoryId) {
		if (productCategoryId == null || productCategoryId <= 0) {
			return null;
		}

		return productCategoryId;
	}

	@GetMapping("/productionList/cutting")
	public String getProductionMaterialCuttingPage(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam("orderIds") List<Long> orderIds, Model model) throws Exception {

		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (!isLowerCabinetProductionMember(member)) {
			throw new AccessDeniedException("자재재단 화면은 하부장 직원만 접근할 수 있습니다.");
		}

		MaterialCuttingPageResponse cuttingData = materialCuttingService.buildCuttingPage(orderIds, member);

		model.addAttribute("cuttingData", cuttingData);
		model.addAttribute("cuttingDataJson", objectMapper.writeValueAsString(cuttingData));

		return "administration/team/production/productionCutting";
	}

	private boolean isLowerCabinetProductionMember(Member member) {
		if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			return false;
		}

		if (member.getTeamCategory() == null) {
			return false;
		}

		String categoryName = member.getTeamCategory().getName();

		return "하부장".equals(categoryName);
	}

	private Sort buildProductionMultiSort(
			List<ProductionSortOrder> sortOrders,
			String dateType
	) {
		List<Sort.Order> springOrders = new ArrayList<>();
		List<String> appliedKeys = new ArrayList<>();

		if (sortOrders != null) {
			for (ProductionSortOrder sortOrder : sortOrders) {
				if (sortOrder == null || appliedKeys.contains(sortOrder.key())) {
					continue;
				}

				String property = switch (sortOrder.key()) {
				case "id" -> "id";
				case "productName" -> "orderItem.productName";
				case "productSeries" -> "orderItem.productionProductSeriesSortValue";
				case "deliveryDate" -> "preferredDeliveryDate";
				default -> null;
				};

				if (property == null) {
					continue;
				}

				Sort.Direction direction = sortOrder.ascending()
						? Sort.Direction.ASC
						: Sort.Direction.DESC;

				springOrders.add(new Sort.Order(direction, property));
				appliedKeys.add(sortOrder.key());
			}
		}

		String defaultDateProperty = "created".equalsIgnoreCase(dateType)
				? "createdAt"
				: "preferredDeliveryDate";

		if (!("preferredDeliveryDate".equals(defaultDateProperty) && appliedKeys.contains("deliveryDate"))) {
			springOrders.add(new Sort.Order(Sort.Direction.DESC, defaultDateProperty));
		}

		if (!appliedKeys.contains("id")) {
			springOrders.add(new Sort.Order(Sort.Direction.DESC, "id"));
		}

		return springOrders.isEmpty() ? Sort.unsorted() : Sort.by(springOrders);
	}

	@GetMapping("/productionList/{orderId}/management-images")
	@ResponseBody
	public ResponseEntity<List<ProductionOverviewImageDto>> getProductionManagementImages(@PathVariable Long orderId,
			@AuthenticationPrincipal(expression = "member") Member loginMember) {
		long startedAt = System.nanoTime();
		log.info("[생산조회 진단][management-images] START orderId={}, memberId={}, username={}, memberCategory={}",
				orderId,
				loginMember != null ? loginMember.getId() : null,
				loginMember != null ? loginMember.getUsername() : null,
				resolveProductionDiagnosticCategory(loginMember));

		try {
			List<ProductionOverviewImageDto> images = teamTaskService.getProductionManagementImages(orderId, loginMember);
			log.info("[생산조회 진단][management-images] END orderId={}, imageCount={}, elapsedMs={}",
					orderId,
					images != null ? images.size() : 0,
					elapsedMillis(startedAt));
			return ResponseEntity.ok(images);
		} catch (RuntimeException e) {
			log.warn("[생산조회 진단][management-images] FAIL orderId={}, elapsedMs={}, message={}",
					orderId, elapsedMillis(startedAt), e.getMessage(), e);
			throw e;
		}
	}

	@GetMapping("/productionList/excel")
    public void downloadProductionListExcel(
            @RequestParam("orderIds") List<Long> orderIds,
            @AuthenticationPrincipal(expression = "member") Member loginMember,
            HttpServletResponse response
    ) throws IOException {
        writeProductionListExcel(
                orderIds,
                ProductionListOutputOptions.defaults(),
                loginMember,
                response
        );
    }

    @PostMapping("/productionList/excel")
    public void downloadProductionListExcelWithOptions(
            @RequestParam("orderIds") List<Long> orderIds,
            @RequestParam(value = "fontSize", defaultValue = "10") int fontSize,
            @RequestParam(value = "includeCompanyName", defaultValue = "true") boolean includeCompanyName,
            @RequestParam(value = "includeDeliveryDate", defaultValue = "false") boolean includeDeliveryDate,
            @RequestParam(value = "filterSummary", required = false) String filterSummary,
            @AuthenticationPrincipal(expression = "member") Member loginMember,
            HttpServletResponse response
    ) throws IOException {
        writeProductionListExcel(
                orderIds,
                new ProductionListOutputOptions(
                        fontSize,
                        includeCompanyName,
                        includeDeliveryDate,
                        filterSummary
                ),
                loginMember,
                response
        );
    }

    @PostMapping("/productionList/print")
    public String printProductionList(
            @RequestParam("orderIds") List<Long> orderIds,
            @RequestParam(value = "fontSize", defaultValue = "10") int fontSize,
            @RequestParam(value = "includeCompanyName", defaultValue = "true") boolean includeCompanyName,
            @RequestParam(value = "includeDeliveryDate", defaultValue = "false") boolean includeDeliveryDate,
            @RequestParam(value = "filterSummary", required = false) String filterSummary,
            @AuthenticationPrincipal(expression = "member") Member loginMember,
            Model model
    ) {
        ProductionListOutputOptions options = new ProductionListOutputOptions(
                fontSize,
                includeCompanyName,
                includeDeliveryDate,
                filterSummary
        );

        List<ProductionListExcelRowDto> rows = teamTaskService
                .getProductionListExcelRowsByOrderIds(orderIds, loginMember);

        model.addAttribute("rows", rows);
        model.addAttribute("options", options);
        model.addAttribute("filterTokens", options.filterTokens());
        model.addAttribute("generatedAt", LocalDateTime.now());
        model.addAttribute("rowCount", rows.size());

        return "administration/team/production/productionListPrint";
    }

    private void writeProductionListExcel(
            List<Long> orderIds,
            ProductionListOutputOptions options,
            Member loginMember,
            HttpServletResponse response
    ) throws IOException {
        List<ProductionListExcelRowDto> rows = teamTaskService
                .getProductionListExcelRowsByOrderIds(orderIds, loginMember);

        String encodedFileName = URLEncoder.encode(
                        "생산팀_제작목록_" + LocalDate.now() + ".xlsx",
                        StandardCharsets.UTF_8
                )
                .replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFileName
        );

        try (Workbook workbook = productionListExcelService.createProductionListWorkbook(rows, options)) {
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        }
    }

	@GetMapping("/productionList/overview-data")
	@ResponseBody
	public ResponseEntity<?> getProductionOverviewData(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) String orderIds) {

		long startedAt = System.nanoTime();

		try {
			Member member = principal.getMember();
			List<Long> parsedOrderIds = parseProductionOverviewOrderIds(orderIds);

			log.info("[생산조회 진단][overview-data] START memberId={}, username={}, memberCategory={}, requestedCount={}, sampleOrderIds={}",
					member != null ? member.getId() : null,
					member != null ? member.getUsername() : null,
					resolveProductionDiagnosticCategory(member),
					parsedOrderIds.size(),
					parsedOrderIds.stream().limit(10).toList());

			List<ProductionOverviewOrderDto> result = teamTaskService.getProductionOverviewOrders(parsedOrderIds, member);
			long canCompleteCount = result.stream().filter(ProductionOverviewOrderDto::isCanComplete).count();
			long canRequestAdminCount = result.stream().filter(ProductionOverviewOrderDto::isCanRequestAdmin).count();

			log.info("[생산조회 진단][overview-data] END memberId={}, resultCount={}, canCompleteCount={}, canRequestAdminCount={}, elapsedMs={}",
					member != null ? member.getId() : null,
					result.size(),
					canCompleteCount,
					canRequestAdminCount,
					elapsedMillis(startedAt));

			return ResponseEntity.ok(result);

		} catch (AccessDeniedException e) {
			log.warn("[생산조회 진단][overview-data] FORBIDDEN elapsedMs={}, message={}",
					elapsedMillis(startedAt), e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));

		} catch (Exception e) {
			log.error("[생산조회 진단][overview-data] FAIL elapsedMs={}, message={}",
					elapsedMillis(startedAt), e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
		}
	}

	private List<Long> parseProductionOverviewOrderIds(String orderIds) {
		if (orderIds == null || orderIds.isBlank()) {
			return List.of();
		}

		return Arrays.stream(orderIds.split(",")).map(String::trim).filter(v -> !v.isBlank()).map(Long::valueOf)
				.filter(Objects::nonNull).distinct().collect(Collectors.toList());
	}

	@PostMapping("/productionList/{orderId}/complete")
	@ResponseBody
	public ResponseEntity<?> completeProductionOrderFromOverview(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId) {

		try {
			Member member = principal.getMember();

			if (isCuttingProductionMember(member)) {
				throw new AccessDeniedException("재단 직원은 생산완료 처리를 할 수 없습니다.");
			}

			ProductionOverviewCompleteResponse response = teamTaskService.completeProductionOrderFromOverview(orderId,
					member);

			return ResponseEntity.ok(response);

		} catch (AccessDeniedException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));

		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
		}
	}

	@PostMapping("/productionList/{orderId}/check")
	@ResponseBody
	public ResponseEntity<?> checkProductionOrder(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId) {

		long startedAt = System.nanoTime();

		try {
			Member member = principal.getMember();
			log.info("[생산조회 진단][check] START orderId={}, memberId={}, username={}, memberCategory={}",
					orderId,
					member != null ? member.getId() : null,
					member != null ? member.getUsername() : null,
					resolveProductionDiagnosticCategory(member));

			ProductionOrderCheckResponse response = teamTaskService.markProductionOrderChecked(orderId, member);

			log.info("[생산조회 진단][check] END orderId={}, checkState={}, revisedBeforeCheck={}, elapsedMs={}",
					orderId,
					response != null ? response.getCheckState() : null,
					response != null && response.isRevisedBeforeCheck(),
					elapsedMillis(startedAt));
			return ResponseEntity.ok(response);

		} catch (AccessDeniedException e) {
			log.warn("[생산조회 진단][check] FORBIDDEN orderId={}, elapsedMs={}, message={}",
					orderId, elapsedMillis(startedAt), e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));

		} catch (Exception e) {
			log.error("[생산조회 진단][check] FAIL orderId={}, elapsedMs={}, message={}",
					orderId, elapsedMillis(startedAt), e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
		}
	}

	@GetMapping("/productionDetail/{orderId}")
	public String getProductionDetail(@PathVariable Long orderId, @AuthenticationPrincipal PrincipalDetails principal,
			Model model) {

		long startedAt = System.nanoTime();

		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member loginMember = principal.getMember();
		log.info("[생산조회 진단][detail-page] START orderId={}, memberId={}, username={}, memberCategory={}",
				orderId, loginMember.getId(), loginMember.getUsername(), resolveProductionDiagnosticCategory(loginMember));

		if (loginMember.getTeam() == null || !"생산팀".equals(loginMember.getTeam().getName())) {
			throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
		}

		/*
		 * 상세 화면에서 사용하는 모든 연관관계를 한 번에 조회합니다.
		 * Task.managedBy, 요청자/업체, 제품, 이미지, 확인상태가 지연 로딩으로 누락되지 않도록
		 * 생산 상세 전용 fetch query를 사용합니다.
		 */
		Order accessOrder = orderRepository.findByIdForProductionDetail(orderId)
				.orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		if (!isProductionListVisibleStatus(accessOrder.getStatus())) {
			throw new AccessDeniedException("생산팀에서 조회할 수 없는 주문 상태입니다.");
		}

		if (!teamTaskService.canAccessProductionOrderForProductionMember(loginMember, accessOrder)) {
			throw new AccessDeniedException("해당 생산 발주를 조회할 권한이 없습니다.");
		}

		// 상세 진입 자체가 확인 처리이며, 재확인 대상이면 변경내역도 함께 모델에 전달합니다.
        ProductionOrderCheckResponse productionCheckResponse =
                teamTaskService.markProductionOrderChecked(orderId, loginMember);

		/*
		 * 확인 처리 직후 최신 checkStatus까지 화면에 표시하기 위해 다시 조회합니다.
		 * 이 재조회가 없으면 최초 진입 시 화면에는 이전 확인상태가 남을 수 있습니다.
		 */
		Order order = orderRepository.findByIdForProductionDetail(orderId)
				.orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		OrderItem orderItem = order.getOrderItem();

		boolean isCuttingProductionMember = isCuttingProductionMember(loginMember);
		boolean isMirrorCuttingProductionMember = isLegacyMirrorCuttingProductionMember(loginMember);

		boolean canRequestAdmin = orderTeamAccessPolicyService
				.canOperateProductionOrder(loginMember, order);
		boolean canChangeStatus = canRequestAdmin
				&& order.getStatus() == OrderStatus.CONFIRMED;

		model.addAttribute("order", order);
		model.addAttribute("orderItem", orderItem);
		model.addAttribute("productionDetailFields",
				teamTaskService.buildProductionOverviewDetailFields(order));
		model.addAttribute("managedByName", teamTaskService.resolveProductionManagedByName(order));
		model.addAttribute("canChangeStatus", canChangeStatus);
		model.addAttribute("canRequestAdmin", canRequestAdmin);
		model.addAttribute("isCuttingProductionMember", isCuttingProductionMember);
		model.addAttribute("isMirrorCuttingProductionMember", isMirrorCuttingProductionMember);
        model.addAttribute("productionCheckResponse", productionCheckResponse);
        model.addAttribute("productionRevisionNotices", productionCheckResponse.getChangeNotices());
        model.addAttribute("productionRevisedBeforeCheck", productionCheckResponse.isRevisedBeforeCheck());

		log.info("[생산조회 진단][detail-page] END orderId={}, orderCategory={}, canChangeStatus={}, canRequestAdmin={}, elapsedMs={}",
				orderId,
				order.getProductCategory() != null ? order.getProductCategory().getName() : "-",
				canChangeStatus,
				canRequestAdmin,
				elapsedMillis(startedAt));

		return "administration/team/production/productionDetail";
	}

	@PostMapping("/updateStatus/{orderId}")
	public String updateProductionStatus(@PathVariable Long orderId, @RequestParam("status") OrderStatus newStatus,
			@AuthenticationPrincipal PrincipalDetails principal, RedirectAttributes redirectAttributes) {

		// 1. 로그인한 멤버 확인
		if (principal == null || principal.getMember() == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "로그인이 필요합니다.");
			return "redirect:/loginForm";
		}

		Member loginMember = principal.getMember();

		if (newStatus != OrderStatus.PRODUCTION_DONE) {
			redirectAttributes.addFlashAttribute("errorMessage", "생산완료 상태로만 변경할 수 있습니다.");
			return "redirect:/team/productionDetail/" + orderId;
		}

		/*
		 * 상세 화면의 기존 상태 변경 경로도 리스트/넓게보기와 같은 중앙 권한 정책 및
		 * 감사·알림 파이프라인을 사용합니다. 화면 파라미터만 조작해 타 카테고리를
		 * 생산완료 처리하거나 중복 로그를 생성하는 우회 경로가 생기지 않도록 합니다.
		 */
		try {
			teamTaskService.completeProductionOrderFromOverview(orderId, loginMember);
			redirectAttributes.addFlashAttribute("successMessage", "상태가 성공적으로 변경되었습니다.");
		} catch (AccessDeniedException | IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}

		return "redirect:/team/productionDetail/" + orderId;
	}

	private String resolveProductionDiagnosticCategory(Member member) {
		if (member == null || member.getTeamCategory() == null) {
			return "-";
		}

		String name = member.getTeamCategory().getName();
		return name == null || name.isBlank() ? "-" : name;
	}

	private long elapsedMillis(long startedAtNanos) {
		return (System.nanoTime() - startedAtNanos) / 1_000_000L;
	}

	@GetMapping("/deliveryList")
	public String getDeliveryOrders(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDate,
			@RequestParam(required = false) OrderStatus status, Model model) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		if (preferredDate == null) {
			preferredDate = LocalDate.now();
		}

		/*
		 * 배송팀 리스트 허용 상태
		 * - CONFIRMED / PRODUCTION_DONE / DISPATCH_DONE:
		 *   직배송/현장배송이면 상단 순서 관리 영역에 표시
		 * - 순서변경/업체별정렬/담당자변경: 위 세 상태 모두 가능
		 * - 배송완료 처리: PRODUCTION_DONE만 가능
		 * - DELIVERY_DONE: 중간 배송완료 영역에서 상세확인만 가능
		 * - 화물/택배/방문 등 기타 배송수단: 기타 영역에서 상세확인만 가능
		 */
		List<OrderStatus> availableDeliveryStatuses = List.of(OrderStatus.CONFIRMED, OrderStatus.PRODUCTION_DONE,
				OrderStatus.DISPATCH_DONE, OrderStatus.DELIVERY_DONE);

		OrderStatus selectedStatus = status;
		if (selectedStatus != null && !availableDeliveryStatuses.contains(selectedStatus)) {
			selectedStatus = null;
		}

		List<OrderStatus> statuses = selectedStatus != null ? List.of(selectedStatus) : availableDeliveryStatuses;

		/*
		 * 과거 row나 관리자가 "동일 담당자 + 배송수단만 변경"한 row가 기존 index range에 남아 있을 수 있으므로 조회 전에 한 번
		 * 정규화합니다.
		 */
		deliveryOrderIndexService.normalizeIndexesForHandlerDate(member.getId(), preferredDate);

		List<DeliveryOrderIndex> all = deliveryOrderIndexService.getDirectDeliveryIndexes(member.getId(), preferredDate,
				statuses);

		List<DeliveryOrderIndex> pendingOrders = all.stream().filter(x -> x.getOrder() != null)
				.filter(x -> deliveryOrderIndexService.isOrderIndexEditableDeliveryOrder(x.getOrder()))
				.collect(Collectors.toList());

		List<DeliveryOrderIndex> doneOrders = all.stream().filter(x -> x.getOrder() != null)
				.filter(x -> deliveryOrderIndexService.isActionableDoneDeliveryOrder(x.getOrder()))
				.collect(Collectors.toList());

		List<DeliveryOrderIndex> otherOrders = all.stream().filter(x -> x.getOrder() != null)
				.filter(x -> deliveryOrderIndexService.isOtherDeliveryListOrder(x.getOrder()))
				.collect(Collectors.toList());

		enrichOrderItems(pendingOrders);
		enrichOrderItems(doneOrders);
		enrichOrderItems(otherOrders);

		model.addAttribute("deliveryHandlerId", member.getId());
		model.addAttribute("preferredDate", preferredDate);

		model.addAttribute("pendingOrders", pendingOrders);
		model.addAttribute("doneOrders", doneOrders);
		model.addAttribute("otherOrders", otherOrders);

		model.addAttribute("status", selectedStatus);
		model.addAttribute("availableStatuses", availableDeliveryStatuses);

		model.addAttribute("deliveryTeamMembers", deliveryOrderIndexService.getActiveDeliveryTeamMembers());

		return "administration/team/delivery/deliveryList";
	}

	private void enrichOrderItems(List<DeliveryOrderIndex> list) {
		if (list == null) {
			return;
		}

		for (DeliveryOrderIndex doi : list) {
			if (doi == null || doi.getOrder() == null) {
				continue;
			}

			OrderItem item = doi.getOrder().getOrderItem();

			if (item == null) {
				continue;
			}

			OrderItemOptionJsonUtil.enrich(item);
			DeliveryProductDisplayUtil.enrich(doi.getOrder());
		}
	}

	@PostMapping("/updateOrderIndex")
	@ResponseBody
	public ResponseEntity<?> updateOrderIndex(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody DeliveryOrderIndexUpdateRequest request) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		if (request.getDeliveryHandlerId() == null || !request.getDeliveryHandlerId().equals(member.getId())) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(담당자 불일치)");
		}

		deliveryOrderIndexService.updateIndexesWithDoneGuard(request);

		return ResponseEntity.ok().build();
	}

	/**
	 * 업체별정렬 API입니다.
	 * 현재 pending DOM 순서를 기준으로 같은 업체 + 같은 실제 배송지를 stable grouping합니다.
	 * 같은 배송일은 request.deliveryDate로 이미 제한하며,
	 * DB 저장은 '순서 저장' 버튼(updateOrderIndex)에서 수행합니다.
	 */
	@PostMapping("/reorderByTask")
	@ResponseBody
	public ResponseEntity<?> reorderByTask(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody DeliveryReorderByTaskRequest request) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		if (request.getDeliveryHandlerId() == null || !request.getDeliveryHandlerId().equals(member.getId())) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(담당자 불일치)");
		}

		if (request.getDeliveryDate() == null) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(날짜 누락)");
		}

		if (request.getPendingOrderIds() == null || request.getPendingOrderIds().isEmpty()) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(정렬 대상 없음)");
		}

		List<Long> reordered = deliveryOrderIndexService.reorderPendingOrderIdsByTask(member.getId(),
				request.getDeliveryDate(), request.getPendingOrderIds());

		return ResponseEntity.ok(new DeliveryReorderByTaskResponse(reordered));
	}

	/**
	 * ✅ 기존 배송완료 컨트롤러: fetch(multipart)에서도 자연스럽게 동작하도록 개선 - 기존 redirect 유지 - 단,
	 * fetch 요청(X-Requested-With=fetch)인 경우 JSON 응답으로 처리
	 */
	@PostMapping("/deliveryStatus/{orderId}")
	public Object updateDeliveryStatusAndUploadImages(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId, @RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "files", required = false) List<MultipartFile> files,
			RedirectAttributes redirectAttributes, HttpServletRequest httpServletRequest) {
		boolean fetchRequest = httpServletRequest != null
				&& "fetch".equalsIgnoreCase(httpServletRequest.getHeader("X-Requested-With"));

		try {
			if (principal == null || principal.getMember() == null) {
				throw new AccessDeniedException("로그인이 필요합니다.");
			}

			Member member = principal.getMember();

			if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
				throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
			}

			if (!OrderStatus.DELIVERY_DONE.name().equals(status)) {
				throw new IllegalStateException("배송팀은 배송완료 처리만 할 수 있습니다.");
			}

			// 직배송/현장배송 + 생산완료(PRODUCTION_DONE) + 현재 로그인 배송담당자 건만 허용
			Order order = deliveryOrderIndexService.getSingleCompletableOrder(member, orderId);

			List<MultipartFile> validFiles = filterValidImageFiles(files);

			if (validFiles.isEmpty()) {
				throw new IllegalStateException("배송완료 이미지는 1장 이상 필요합니다.");
			}

			deliveryCompletionService.completeSingle(member, order.getId(), validFiles);

			if (fetchRequest) {
				Map<String, Object> body = new HashMap<>();
				body.put("success", true);
				body.put("orderId", orderId);
				body.put("status", OrderStatus.DELIVERY_DONE.name());
				body.put("message", "배송완료 처리되었습니다.");

				return ResponseEntity.ok(body);
			}

			redirectAttributes.addFlashAttribute("successMessage", "배송완료 처리되었습니다.");

		} catch (Exception e) {
			e.printStackTrace();

			if (fetchRequest) {
				Map<String, Object> body = new HashMap<>();
				body.put("success", false);
				body.put("orderId", orderId);
				body.put("message", e.getMessage() != null ? e.getMessage() : "배송 상태 변경 실패");

				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
			}

			redirectAttributes.addFlashAttribute("errorMessage",
					"배송 상태 변경 실패: " + (e.getMessage() != null ? e.getMessage() : "알 수 없는 오류"));
		}

		return "redirect:/team/deliveryDetail/" + orderId;
	}

	@PostMapping("/deliveryStatus/{orderId}/same-address")
	@ResponseBody
	public ResponseEntity<?> updateSameAddressDeliveryStatusAndUploadImages(
			@AuthenticationPrincipal PrincipalDetails principal, @PathVariable Long orderId,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "files", required = false) List<MultipartFile> files) {
		try {
			if (principal == null || principal.getMember() == null) {
				throw new AccessDeniedException("로그인이 필요합니다.");
			}

			Member member = principal.getMember();

			if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
				throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
			}

			if (!OrderStatus.DELIVERY_DONE.name().equals(status)) {
				throw new IllegalStateException("배송팀은 배송완료 처리만 할 수 있습니다.");
			}

			List<Order> targetOrders = deliveryOrderIndexService
					.findSameCompanySameAddressSameDeliveryDateCompletableOrders(member, orderId);

			if (targetOrders == null || targetOrders.isEmpty()) {
				throw new IllegalStateException("동일 업체/동일 주소/동일 배송일 기준 배송완료 처리 대상이 없습니다.");
			}

			List<MultipartFile> validFiles = filterValidImageFiles(files);

			if (validFiles.isEmpty()) {
				throw new IllegalStateException("동일주소 배송완료 이미지는 1장 이상 필요합니다.");
			}

			List<Long> targetOrderIds = targetOrders.stream()
					.map(Order::getId)
					.filter(Objects::nonNull)
					.distinct()
					.collect(Collectors.toList());

			List<Long> completedOrderIds = deliveryCompletionService
					.completeSameAddress(member, targetOrderIds, validFiles);

			Map<String, Object> body = new HashMap<>();
			body.put("success", true);
			body.put("completedOrderIds", completedOrderIds);
			body.put("completedCount", completedOrderIds.size());
			body.put("uploadedImageCount", validFiles.size());
			body.put("message", completedOrderIds.size() + "건 배송완료 처리되었습니다.");

			return ResponseEntity.ok(body);

		} catch (Exception e) {
			e.printStackTrace();

			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("orderId", orderId);
			body.put("message", e.getMessage() != null ? e.getMessage() : "동일주소 배송완료 처리 실패");

			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
		}
	}

	private List<MultipartFile> filterValidImageFiles(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			return List.of();
		}

		return files.stream().filter(Objects::nonNull).filter(file -> !file.isEmpty()).filter(file -> {
			String contentType = file.getContentType();
			return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
		}).collect(Collectors.toList());
	}

	@GetMapping("/deliveryStatus/{orderId}/same-address/preview")
	@ResponseBody
	public ResponseEntity<?> getSameAddressDeliveryCompletePreview(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId) {
		try {
			if (principal == null || principal.getMember() == null) {
				throw new AccessDeniedException("로그인이 필요합니다.");
			}

			Member member = principal.getMember();

			if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
				throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
			}

			List<Order> targetOrders = deliveryOrderIndexService
					.findSameCompanySameAddressSameDeliveryDateCompletableOrders(member, orderId);

			List<Long> targetOrderIds = targetOrders.stream().map(Order::getId).collect(Collectors.toList());

			String sourceDeliveryDateText = targetOrders.isEmpty() ? "" : resolveDeliveryDateText(targetOrders.get(0));

			Map<String, Object> body = new HashMap<>();
			body.put("success", true);
			body.put("targetOrderIds", targetOrderIds);
			body.put("targetCount", targetOrderIds.size());
			body.put("requiredImageCount", targetOrderIds.isEmpty() ? 0 : 1);
			body.put("deliveryDate", sourceDeliveryDateText);
			body.put("message", "완료 대상 " + targetOrderIds.size() + "건 / 이미지 1장 이상 필요");

			return ResponseEntity.ok(body);

		} catch (Exception e) {
			e.printStackTrace();

			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("orderId", orderId);
			body.put("targetCount", 0);
			body.put("requiredImageCount", 0);
			body.put("message", e.getMessage() != null ? e.getMessage() : "배송완료 대상 조회 실패");

			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
		}
	}

	private String resolveDeliveryDateText(Order order) {
		if (order == null || order.getPreferredDeliveryDate() == null) {
			return "";
		}

		return order.getPreferredDeliveryDate().toLocalDate().toString();
	}

	@GetMapping("/deliveryOrderSummary/{orderId}")
	@ResponseBody
	public ResponseEntity<?> getDeliveryOrderSummary(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		DeliveryOrderSummaryRes res = deliveryOrderSummaryService.getSummary(member.getId(), orderId);

		Map<String, Object> body = objectMapper.convertValue(res, new TypeReference<Map<String, Object>>() {
		});

		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		body.put("productText", buildDeliveryModalProductText(order));
		body.put("ordererPhone", order != null && StringUtils.hasText(order.getOrdererPhone()) ? order.getOrdererPhone().trim() : "-");

		return ResponseEntity.ok(body);
	}

	private String buildDeliveryModalProductText(Order order) {
		List<String> lines = new ArrayList<>();
		OrderItem item = order != null ? order.getOrderItem() : null;

		if (item != null) {
			OrderItemOptionJsonUtil.enrich(item);
			DeliveryProductDisplayUtil.enrich(order);
		}

		String categoryText = item != null && StringUtils.hasText(item.getDeliveryCategoryText())
				? item.getDeliveryCategoryText().trim()
				: (order != null && order.getProductCategory() != null && StringUtils.hasText(order.getProductCategory().getName())
						? order.getProductCategory().getName().trim()
						: "-");

		String productNameText = item != null && StringUtils.hasText(item.getDeliveryProductName())
				? item.getDeliveryProductName().trim()
				: (item != null && StringUtils.hasText(item.getProductName()) ? item.getProductName().trim() : "-");

		String sizeText = item != null && StringUtils.hasText(item.getDeliverySizeText())
				? item.getDeliverySizeText().trim()
				: "-";

		String colorText = item != null && StringUtils.hasText(item.getDeliveryColorText())
				? item.getDeliveryColorText().trim()
				: "-";

		int quantity = resolveDeliveryModalQuantity(order, item);
		String adminMemoText = resolveDeliveryAdminMemoText(order);

		lines.add("카테고리: " + categoryText);
		lines.add("제품명: " + productNameText);
		lines.add("사이즈: " + sizeText);
		lines.add("색상: " + colorText);
		lines.add(quantity > 0 ? "수량: " + quantity + "개" : "수량: -");
		lines.add("관리자메모: " + adminMemoText);

		return String.join("\n", lines);
	}

	private int resolveDeliveryModalQuantity(Order order, OrderItem item) {
		if (item != null && item.getQuantity() > 0) {
			return item.getQuantity();
		}

		if (order != null && order.getQuantity() > 0) {
			return order.getQuantity();
		}

		return 0;
	}

	private String resolveDeliveryAdminMemoText(Order order) {
		if (order == null || !StringUtils.hasText(order.getAdminMemo())) {
			return "-";
		}

		return order.getAdminMemo()
				.replace("\r", " ")
				.replace("\n", " ")
				.replace("\t", " ")
				.replaceAll("\\s{2,}", " ")
				.trim();
	}
	/**
	 * ✅ 엑셀 출력 (현재 DOM 순서 그대로 전송받아 A4 맞춤 XLSX 생성)
	 */
	@PostMapping("/deliveryExcel")
	public ResponseEntity<?> downloadDeliveryExcel(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody DeliveryExcelRequest request) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		if (request == null) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(요청 데이터 누락)");
		}

		if (request.getDeliveryHandlerId() == null || !request.getDeliveryHandlerId().equals(member.getId())) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(담당자 불일치)");
		}

		if (request.getOrderedOrderIds() == null || request.getOrderedOrderIds().isEmpty()) {
			return ResponseEntity.badRequest().body("잘못된 요청입니다.(출력 대상 없음)");
		}

		String deliveryHandlerName = StringUtils.hasText(member.getName())
				? member.getName().trim()
				: member.getUsername();

		byte[] bytes = deliveryExcelService.buildExcel(member.getId(), deliveryHandlerName, request.getFromDate(),
				request.getToDate(), request.getOrderedOrderIds());

		String dateLabel = resolveDeliveryExcelDateLabel(request.getFromDate(), request.getToDate());
		String filename = "delivery_" + dateLabel + ".xlsx";

		return ResponseEntity.ok()
				.header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"").body(bytes);
	}

	private String resolveDeliveryExcelDateLabel(LocalDate fromDate, LocalDate toDate) {
		if (fromDate != null && toDate != null) {
			if (fromDate.equals(toDate)) {
				return String.valueOf(fromDate);
			}

			return fromDate + "_" + toDate;
		}

		if (fromDate != null) {
			return String.valueOf(fromDate);
		}

		if (toDate != null) {
			return String.valueOf(toDate);
		}

		return "current";
	}

	@PostMapping("/deliveryHandler/bulk")
	@ResponseBody
	public ResponseEntity<?> changeDeliveryHandlers(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestBody DeliveryHandlerBulkChangeRequest request) {
		try {
			if (principal == null || principal.getMember() == null) {
				throw new AccessDeniedException("로그인이 필요합니다.");
			}

			Member member = principal.getMember();

			if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
				throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
			}

			List<Long> changedOrderIds = deliveryHandlerChangeAuditService.changeDeliveryHandlers(member,
					request != null ? request.getOrderIds() : null, request != null ? request.getNewHandlerId() : null);

			Map<String, Object> body = new HashMap<>();
			body.put("success", true);
			body.put("changedOrderIds", changedOrderIds);
			body.put("changedCount", changedOrderIds.size());
			body.put("message", changedOrderIds.size() + "건의 담당자가 변경되었습니다.");

			return ResponseEntity.ok(body);

		} catch (Exception e) {
			e.printStackTrace();

			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("message", e.getMessage() != null ? e.getMessage() : "담당자 일괄 변경 실패");

			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
		}
	}

	@PostMapping("/deliveryHandler/{orderId}")
	@ResponseBody
	public ResponseEntity<?> changeDeliveryHandler(@AuthenticationPrincipal PrincipalDetails principal,
			@PathVariable Long orderId, @RequestBody DeliveryHandlerChangeRequest request) {
		try {
			if (principal == null || principal.getMember() == null) {
				throw new AccessDeniedException("로그인이 필요합니다.");
			}

			Member member = principal.getMember();

			if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
				throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
			}

			deliveryHandlerChangeAuditService.changeDeliveryHandler(member, orderId,
					request != null ? request.getNewHandlerId() : null);

			Map<String, Object> body = new HashMap<>();
			body.put("success", true);
			body.put("orderId", orderId);
			body.put("message", "담당자가 변경되었습니다.");

			return ResponseEntity.ok(body);

		} catch (Exception e) {
			e.printStackTrace();

			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("orderId", orderId);
			body.put("message", e.getMessage() != null ? e.getMessage() : "담당자 변경 실패");

			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
		}
	}

	@GetMapping("/deliveryDetail/{id}")
	public String getDeliveryDetailPage(@PathVariable Long id, @AuthenticationPrincipal PrincipalDetails principal,
			Model model) {
		if (principal == null || principal.getMember() == null) {
			throw new AccessDeniedException("로그인이 필요합니다.");
		}

		Member member = principal.getMember();

		if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
		}

		Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

		OrderItem orderItem = order.getOrderItem();

		if (orderItem != null && orderItem.getOptionJson() != null && !orderItem.getOptionJson().isBlank()) {
			try {
				Map<String, String> parsedMap = objectMapper.readValue(orderItem.getOptionJson(),
						new TypeReference<Map<String, String>>() {
						});

				orderItem.setParsedOptionMap(parsedMap);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (orderItem != null) {
			OrderItemOptionJsonUtil.enrich(orderItem);
		}

		boolean canCompleteDelivery = deliveryOrderIndexService.isCompletableByDeliveryTeam(order);

		boolean canChangeDeliveryHandler = deliveryOrderIndexService.isActionablePendingDeliveryOrder(order);

		model.addAttribute("order", order);
		model.addAttribute("orderItem", orderItem);

		model.addAttribute("canCompleteDelivery", canCompleteDelivery);
		model.addAttribute("canChangeDeliveryHandler", canChangeDeliveryHandler);
		model.addAttribute("deliveryTeamMembers", deliveryOrderIndexService.getActiveDeliveryTeamMembers());

		return "administration/team/delivery/deliveryDetail";
	}

	@GetMapping("/asList")
	public String getAsList(@AuthenticationPrincipal PrincipalDetails principal,

			@RequestParam(required = false, defaultValue = "requested") String dateType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

			@RequestParam(required = false) String status, @RequestParam(required = false) String companyKeyword,

			@RequestParam(required = false) Long provinceId, @RequestParam(required = false) Long cityId,
			@RequestParam(required = false) Long districtId,

			@RequestParam(required = false) String visitTimeSort,
			@RequestParam(required = false) String scheduledDateSort,
			@RequestParam(required = false) String addressSort, @RequestParam(required = false) String statusSort,

			Pageable pageable, Model model) {

		Member member = principal.getMember();

		if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
		}

		// AS팀 리스트에서는 진행중/완료만 허용
		AsStatus statusEnum = parseAsTeamListStatus(status);

		LocalDateTime start = null;
		LocalDateTime end = null;

		/*
		 * 중요: requested / processed 는 [start, end+1day) scheduled 는
		 * AsTaskSchedule.scheduledDate(LocalDate) 기준이므로 endDate 그대로 넘겨서 서비스에서 LocalDate
		 * 비교(<=) 하도록 처리
		 */
		if ("scheduled".equalsIgnoreCase(dateType)) {
			start = (startDate != null) ? startDate.atStartOfDay() : null;
			end = (endDate != null) ? endDate.atStartOfDay() : null;
		} else {
			start = (startDate != null) ? startDate.atStartOfDay() : null;
			end = (endDate != null) ? endDate.plusDays(1).atStartOfDay() : null;
		}

		String normalizedAddressSort = normalizeSortDirection(addressSort);
		String normalizedStatusSort = normalizeSortDirection(statusSort);

		Page<AsTask> asPage = asTaskService.getAsTasksForAsTeamList(member, dateType, start, end, statusEnum,
				companyKeyword, provinceId, cityId, districtId, visitTimeSort, scheduledDateSort, normalizedAddressSort,
				normalizedStatusSort, pageable);

		model.addAttribute("provinces", provinceRepository.findAll());

		model.addAttribute("asPage", asPage);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("dateType", dateType);

		model.addAttribute("selectedStatus", statusEnum);
		model.addAttribute("selectedStatusName", statusEnum != null ? statusEnum.name() : null);

		model.addAttribute("companyKeyword", companyKeyword);
		model.addAttribute("provinceId", provinceId);
		model.addAttribute("cityId", cityId);
		model.addAttribute("districtId", districtId);

		model.addAttribute("visitTimeSort", visitTimeSort);
		model.addAttribute("scheduledDateSort", scheduledDateSort);
		model.addAttribute("addressSort", normalizedAddressSort);
		model.addAttribute("statusSort", normalizedStatusSort);

		model.addAttribute("asStatusLabels", AsStatus.labelMap());

		// 방문예정일 + (n번째) 표시용
		model.addAttribute("asScheduleDisplayMap", asTaskService.getScheduleDisplayMap(asPage.getContent()));

		// 같은 주소끼리 옅은 배경색 그룹 표시용
		model.addAttribute("addressGroupClassMap", asTaskService.getAddressGroupClassMap(asPage.getContent()));

		return "administration/team/as/asList";
	}

	/**
	 * AS팀 리스트 전용 상태 파서 - 진행중 / 완료만 허용 - REQUESTED, CANCELED, 이상값은 전부 null 처리 =>
	 * "전체(진행중+완료)"
	 */
	private AsStatus parseAsTeamListStatus(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}

		try {
			AsStatus parsed = AsStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
			if (parsed == AsStatus.IN_PROGRESS || parsed == AsStatus.COMPLETED) {
				return parsed;
			}
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String normalizeSortDirection(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}

		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		if (!"asc".equals(normalized) && !"desc".equals(normalized)) {
			return null;
		}
		return normalized;
	}

	@GetMapping("/asDetail/{id}")
	public String asDetail(@PathVariable Long id, Model model, @AuthenticationPrincipal PrincipalDetails principal) {

		Member loginMember = principal != null ? principal.getMember() : null;
		validateAsTeamMember(loginMember);

		AsTask asTask = asTaskService.getAsDetailForAssignedHandler(id, loginMember);
		LocalDate visitPlannedDate = asTaskService.getVisitPlannedDate(id);
		List<Member> asTeamMembers = asTaskService.getActiveAsTeamMembers();

		model.addAttribute("asTask", asTask);
		model.addAttribute("visitPlannedDate", visitPlannedDate);
		model.addAttribute("asStatuses", AsStatus.values());
		model.addAttribute("asStatusLabels", AsStatus.labelMap());
		model.addAttribute("asTeamMembers", asTeamMembers);

		return "administration/team/as/asDetail";
	}

	@PostMapping("/asUpdate/{id}")
	public String updateAsTaskFromTeam(@PathVariable Long id, @AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(value = "assignedHandlerId", required = false) Long assignedHandlerId,
			@RequestParam(value = "status", required = false) AsStatus status,
			@RequestParam(value = "handlerMemo", required = false) String handlerMemo,
			@RequestParam(value = "visitPlannedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitPlannedDate,
			@RequestParam(value = "visitPlannedTime", required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime visitPlannedTime,
			@RequestParam(value = "resultImages", required = false) List<MultipartFile> resultImages,
			RedirectAttributes redirectAttributes) {

		Member loginMember = principal != null ? principal.getMember() : null;
		validateAsTeamMember(loginMember);

		try {
			boolean handlerChanged = asTaskService.updateAsTaskByHandler(id, loginMember, assignedHandlerId, status,
					handlerMemo, visitPlannedDate, visitPlannedTime, resultImages);

			if (handlerChanged) {
				redirectAttributes.addFlashAttribute("success",
						"담당자가 변경되어 기존 방문 일정이 삭제되었습니다. 현재 계정에서는 더 이상 해당 AS 상세를 조회할 수 없습니다.");
				return "redirect:/team/asList";
			}

			redirectAttributes.addFlashAttribute("success", "AS 상태, 담당자, 담당자용 메모, 방문예정일, 방문예정시간, 결과 이미지가 저장되었습니다.");
			return "redirect:/team/asDetail/" + id;

		} catch (Exception e) {
			e.printStackTrace();
			redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "저장 중 오류가 발생했습니다.");
			return "redirect:/team/asDetail/" + id;
		}
	}

	private void validateAsTeamMember(Member member) {
		if (member == null || member.getTeam() == null || !AS_TEAM_ID.equals(member.getTeam().getId())) {
			throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
		}
	}

	@GetMapping("/asDetailModal/{id}")
	@ResponseBody
	public TeamAsDetailModalResponse getAsDetailModal(@PathVariable Long id,
			@AuthenticationPrincipal PrincipalDetails principal) {

		Member member = principal.getMember();

		if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
		}

		return asTaskService.getAsTaskDetailModal(id, member);
	}

	@DeleteMapping("/asImageDelete/{id}")
	@ResponseBody
	public ResponseEntity<Void> deleteAsImage(@PathVariable Long id) {
		Optional<AsImage> imageOpt = asImageRepository.findById(id);
		if (imageOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		AsImage image = imageOpt.get();

		// 파일 삭제
		if (image.getPath() != null) {
			try {
				Files.deleteIfExists(Paths.get(image.getPath()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		asImageRepository.delete(image);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/productionStickerPrint")
	public String productionStickerPrint(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam("orderIds") List<Long> orderIds, Model model) {
		Member member = principal.getMember();

		if (member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
			throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
		}

		if (orderIds == null || orderIds.isEmpty()) {
			model.addAttribute("pages", List.of());
			model.addAttribute("totalCount", 0);
			model.addAttribute("today", LocalDate.now()); // ✅ 추가
			return "administration/team/production/productionStickerPrint";
		}

		List<Long> accessibleOrderIds = resolveAccessibleProductionOrderIdsForStickerPrint(orderIds, member);

		List<StickerPrintDto> items = teamTaskService.getStickerPrintItems(accessibleOrderIds, null);

		List<List<StickerPrintDto>> pages = new ArrayList<>();
		for (int i = 0; i < items.size(); i += 4) {
			pages.add(items.subList(i, Math.min(i + 4, items.size())));
		}

		model.addAttribute("pages", pages);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("today", LocalDate.now()); // ✅ 추가

		return "administration/team/production/productionStickerPrint";
	}

	private List<Long> resolveAccessibleProductionOrderIdsForStickerPrint(List<Long> orderIds, Member member) {
		if (orderIds == null || orderIds.isEmpty()) {
			return List.of();
		}

		List<Long> distinctOrderIds = orderIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());

		if (distinctOrderIds.isEmpty()) {
			return List.of();
		}

		List<Order> orders = orderRepository.findAllForStickerPrint(distinctOrderIds);
		Map<Long, Order> orderMap = orders.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

		return orderIds.stream()
				.filter(Objects::nonNull)
				.filter(orderId -> {
					Order order = orderMap.get(orderId);
					return order != null && teamTaskService.canAccessProductionOrderForProductionMember(member, order);
				})
				.distinct()
				.collect(Collectors.toList());
	}
}
