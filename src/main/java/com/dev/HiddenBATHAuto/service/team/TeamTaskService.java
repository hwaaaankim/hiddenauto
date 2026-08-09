package com.dev.HiddenBATHAuto.service.team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.dto.production.ProductionCheckViewDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionListExcelRowDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOrderCheckResponse;
import com.dev.HiddenBATHAuto.dto.production.ProductionSortOrder;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewCompleteResponse;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewFieldDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewImageDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOverviewOrderDto;
import com.dev.HiddenBATHAuto.dto.production.StickerPrintDto;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderCheckStatus;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderCheckStatusRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.OrderChangeAuditService;
import com.dev.HiddenBATHAuto.service.order.OrderTeamAccessPolicyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamTaskService {

	private final OrderRepository orderRepository;
	private final AsTaskRepository asTaskRepository;
	private final ObjectMapper objectMapper;
	private final OrderCheckStatusRepository orderCheckStatusRepository;
	private final OrderChangeAuditService orderChangeAuditService;
	private final OrderTeamAccessPolicyService accessPolicyService;
	private static final List<OrderStatus> PRODUCTION_LIST_VISIBLE_STATUSES = List.of(
			OrderStatus.CONFIRMED,
			OrderStatus.PRODUCTION_DONE,
			OrderStatus.DISPATCH_DONE,
			OrderStatus.DELIVERY_DONE
	);
	private static final int PRODUCTION_CHECK_VIEW_BATCH_SIZE = 500;

	private static final Long MIRROR_CUTTING_TEAM_CATEGORY_ID = 14L;
	private static final String MIRROR_CUTTING_TEAM_CATEGORY_NAME = "재단(거울)";
	private static final List<String> MIRROR_CUTTING_ACCESS_TEAM_CATEGORY_NAMES = List.of(
			"거울",
			"LED거울",
			MIRROR_CUTTING_TEAM_CATEGORY_NAME
	);

	/**
	 * 기존 단건 호출부 호환용입니다. orderId를 FROM/TO에 동일하게 전달합니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
            Long categoryId,
            Long orderId,
            String productNameKeyword,
            String dateType,
            OrderStatus statusFilter,
            LocalDateTime start,
            LocalDateTime end,
            boolean mirrorCuttingOnly,
            Long memberId,
            boolean prioritizeUnchecked,
            Pageable pageable
    ) {
        return getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
                categoryId,
                orderId,
                orderId,
                productNameKeyword,
                null,
                dateType,
                statusFilter,
                start,
                end,
                mirrorCuttingOnly,
                memberId,
                prioritizeUnchecked,
                pageable
        );
    }

	/**
	 * 기존 단건 + 규격 조건 호출부 호환용입니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
            Long categoryId,
            Long orderId,
            String productNameKeyword,
            Boolean standard,
            String dateType,
            OrderStatus statusFilter,
            LocalDateTime start,
            LocalDateTime end,
            boolean mirrorCuttingOnly,
            Long memberId,
            boolean prioritizeUnchecked,
            Pageable pageable
    ) {
        return getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
                categoryId,
                orderId,
                orderId,
                productNameKeyword,
                standard,
                dateType,
                statusFilter,
                start,
                end,
                mirrorCuttingOnly,
                memberId,
                prioritizeUnchecked,
                pageable
        );
    }

	/**
	 * 생산팀 목록을 오더 ID 포함 범위로 조회하고 개인 체크상태 기본 정렬을 적용합니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
            Long categoryId,
            Long orderIdFrom,
            Long orderIdTo,
            String productNameKeyword,
            Boolean standard,
            String dateType,
            OrderStatus statusFilter,
            LocalDateTime start,
            LocalDateTime end,
            boolean mirrorCuttingOnly,
            Long memberId,
            boolean prioritizeUnchecked,
            Pageable pageable
    ) {
        boolean useCreated = "created".equalsIgnoreCase(dateType);

        OrderStatus effectiveStatusFilter = normalizeProductionListStatusFilter(statusFilter);
        boolean allStatus = (effectiveStatusFilter == null);
        String normalizedProductNameKeyword = normalizeKeyword(productNameKeyword);

        Page<Order> page;

        if (useCreated) {
            page = orderRepository.findProductionListByCreatedRangeStatusCheckSortedWithOrderIdRange(
                    categoryId,
                    mirrorCuttingOnly,
                    orderIdFrom,
                    orderIdTo,
                    normalizedProductNameKeyword,
                    standard,
                    allStatus,
                    effectiveStatusFilter,
                    PRODUCTION_LIST_VISIBLE_STATUSES,
                    start,
                    end,
                    memberId,
                    OrderWorkArea.PRODUCTION,
                    prioritizeUnchecked,
                    pageable
            );
        } else {
            page = orderRepository.findProductionListByPreferredRangeStatusCheckSortedWithOrderIdRange(
                    categoryId,
                    mirrorCuttingOnly,
                    orderIdFrom,
                    orderIdTo,
                    normalizedProductNameKeyword,
                    standard,
                    allStatus,
                    effectiveStatusFilter,
                    PRODUCTION_LIST_VISIBLE_STATUSES,
                    start,
                    end,
                    memberId,
                    OrderWorkArea.PRODUCTION,
                    prioritizeUnchecked,
                    pageable
            );
        }

        applySingleLineOptionSummary(page);
        return page;
    }

	@Transactional
    public ProductionOrderCheckResponse markProductionOrderChecked(Long orderId, Member loginMember) {
        validateProductionTeamMember(loginMember);

        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID가 없습니다.");
        }

        Order order = orderRepository.findByIdForProductionCheck(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 발주를 찾을 수 없습니다."));

        if (!accessPolicyService.canViewProductionOrder(loginMember, order)) {
            throw new AccessDeniedException("해당 발주를 확인 처리할 권한이 없습니다.");
        }

        OrderChangeAuditService.OrderMemberCheckResult result = orderChangeAuditService.markChecked(
                order,
                loginMember,
                OrderWorkArea.PRODUCTION
        );

        return ProductionOrderCheckResponse.builder()
                .orderId(orderId)
                .checked(true)
                .checkState(OrderCheckState.CHECKED.name())
                .checkStateLabel(OrderCheckState.CHECKED.getLabel())
                .checkedByUsername(result.checkedByUsername())
                .checkedAtText(formatDateTime(result.checkedAt()))
                .revisedBeforeCheck(result.revisedBeforeCheck())
                .changeNotices(result.changeNotices())
                .message(result.revisedBeforeCheck()
                        ? "관리자 또는 다른 업무팀의 변경 내용을 확인 처리했습니다."
                        : "확인 처리되었습니다.")
                .build();
    }

	private OrderCheckState resolveCheckState(OrderCheckStatus checkStatus) {
	    if (checkStatus == null) {
	        return OrderCheckState.UNCHECKED;
	    }

	    return checkStatus.getResolvedCheckState();
	}

	private String resolveCheckedByUsername(Member member) {
	    if (member == null) {
	        return "UNKNOWN";
	    }

	    if (member.getName() != null && !member.getName().isBlank()) {
	        return member.getName();
	    }

	    if (member.getId() != null) {
	        return "MEMBER-" + member.getId();
	    }

	    return "UNKNOWN";
	}
	
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
	 * 기존 단건 호출부 호환용입니다. orderId를 FROM/TO에 동일하게 전달합니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilter(
	        Long categoryId,
	        Long orderId,
            String productNameKeyword,
	        String dateType,
	        OrderStatus statusFilter,
	        LocalDateTime start,
	        LocalDateTime end,
	        boolean mirrorCuttingOnly,
	        Pageable pageable
	) {
        return getProductionOrdersByDateTypeAndStatusFilter(
                categoryId,
                orderId,
                orderId,
                productNameKeyword,
                null,
                dateType,
                statusFilter,
                start,
                end,
                mirrorCuttingOnly,
                pageable
        );
    }

	/**
	 * 기존 단건 + 규격 조건 호출부 호환용입니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilter(
	        Long categoryId,
	        Long orderId,
            String productNameKeyword,
            Boolean standard,
	        String dateType,
	        OrderStatus statusFilter,
	        LocalDateTime start,
	        LocalDateTime end,
	        boolean mirrorCuttingOnly,
	        Pageable pageable
	) {
        return getProductionOrdersByDateTypeAndStatusFilter(
                categoryId,
                orderId,
                orderId,
                productNameKeyword,
                standard,
                dateType,
                statusFilter,
                start,
                end,
                mirrorCuttingOnly,
                pageable
        );
    }

	/**
	 * 생산팀 목록을 상태, 기간, 제품명, 규격 여부, 오더 ID 포함 범위로 조회합니다.
	 * standard가 null이면 규격/비규격 전체를 조회합니다.
	 */
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilter(
	        Long categoryId,
	        Long orderIdFrom,
	        Long orderIdTo,
            String productNameKeyword,
            Boolean standard,
	        String dateType,
	        OrderStatus statusFilter,
	        LocalDateTime start,
	        LocalDateTime end,
	        boolean mirrorCuttingOnly,
	        Pageable pageable
	) {
		boolean useCreated = "created".equalsIgnoreCase(dateType);
        String normalizedProductNameKeyword = normalizeKeyword(productNameKeyword);

		OrderStatus effectiveStatusFilter = normalizeProductionListStatusFilter(statusFilter);
		boolean allStatus = (effectiveStatusFilter == null);

		Page<Order> page;

		if (useCreated) {
		    page = orderRepository.findProductionListByCreatedRangeStatusSortableWithOrderIdRange(
		            categoryId,
		            mirrorCuttingOnly,
		            orderIdFrom,
		            orderIdTo,
                    normalizedProductNameKeyword,
                    standard,
		            allStatus,
		            effectiveStatusFilter,
		            PRODUCTION_LIST_VISIBLE_STATUSES,
		            start,
		            end,
		            pageable
		    );
		} else {
		    page = orderRepository.findProductionListByPreferredRangeStatusSortableWithOrderIdRange(
		            categoryId,
		            mirrorCuttingOnly,
		            orderIdFrom,
		            orderIdTo,
                    normalizedProductNameKeyword,
                    standard,
		            allStatus,
		            effectiveStatusFilter,
		            PRODUCTION_LIST_VISIBLE_STATUSES,
		            start,
		            end,
		            pageable
		    );
		}

		applySingleLineOptionSummary(page);

		return page;
	}

	/**
	 * 기존 단건 다중 정렬 호출부 호환용입니다.
	 */
	@Transactional(readOnly = true)
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilterMultiSorted(
			Long categoryId,
			Long orderId,
			String productNameKeyword,
			Boolean standard,
			String dateType,
			OrderStatus statusFilter,
			LocalDateTime start,
			LocalDateTime end,
			boolean mirrorCuttingOnly,
			Member loginMember,
			List<ProductionSortOrder> sortOrders,
			Pageable pageable
	) {
		return getProductionOrdersByDateTypeAndStatusFilterMultiSorted(
				categoryId,
				orderId,
				orderId,
				productNameKeyword,
				standard,
				dateType,
				statusFilter,
				start,
				end,
				mirrorCuttingOnly,
				loginMember,
				sortOrders,
				pageable
		);
	}

	/**
	 * 화면에서 선택한 여러 정렬 조건을 클릭 순서대로 적용합니다.
	 * 오더 ID는 FROM/TO 포함 범위로 제한합니다.
	 */
	@Transactional(readOnly = true)
	public Page<Order> getProductionOrdersByDateTypeAndStatusFilterMultiSorted(
			Long categoryId,
			Long orderIdFrom,
			Long orderIdTo,
			String productNameKeyword,
			Boolean standard,
			String dateType,
			OrderStatus statusFilter,
			LocalDateTime start,
			LocalDateTime end,
			boolean mirrorCuttingOnly,
			Member loginMember,
			List<ProductionSortOrder> sortOrders,
			Pageable pageable
	) {
		validateProductionTeamMember(loginMember);

		boolean useCreated = "created".equalsIgnoreCase(dateType);
		String normalizedProductNameKeyword = normalizeKeyword(productNameKeyword);
		OrderStatus effectiveStatusFilter = normalizeProductionListStatusFilter(statusFilter);
		boolean allStatus = effectiveStatusFilter == null;

		List<Order> orders = useCreated
				? orderRepository.findProductionListByCreatedRangeStatusForMultiSortWithOrderIdRange(
						categoryId, mirrorCuttingOnly, orderIdFrom, orderIdTo, normalizedProductNameKeyword, standard,
						allStatus, effectiveStatusFilter, PRODUCTION_LIST_VISIBLE_STATUSES, start, end)
				: orderRepository.findProductionListByPreferredRangeStatusForMultiSortWithOrderIdRange(
						categoryId, mirrorCuttingOnly, orderIdFrom, orderIdTo, normalizedProductNameKeyword, standard,
						allStatus, effectiveStatusFilter, PRODUCTION_LIST_VISIBLE_STATUSES, start, end);

		if (orders == null || orders.isEmpty()) {
			return pageable == null || pageable.isUnpaged()
					? new PageImpl<>(List.of())
					: new PageImpl<>(List.of(), pageable, 0);
		}

		applySingleLineOptionSummary(orders);

		List<Long> orderIds = orders.stream()
				.map(Order::getId)
				.filter(Objects::nonNull)
				.toList();

		Map<Long, ProductionCheckViewDto> checkViewMap = getProductionCheckViewMapInBatches(
				orderIds,
				loginMember
		);

		orders.sort(buildProductionMultiComparator(sortOrders, checkViewMap, dateType));

		if (pageable == null || pageable.isUnpaged()) {
			return new PageImpl<>(new ArrayList<>(orders));
		}

		int total = orders.size();
		int fromIndex = (int) Math.min(pageable.getOffset(), total);
		int toIndex = Math.min(fromIndex + pageable.getPageSize(), total);

		return new PageImpl<>(
				new ArrayList<>(orders.subList(fromIndex, toIndex)),
				pageable,
				total
		);
	}

	private Comparator<Order> buildProductionMultiComparator(
			List<ProductionSortOrder> sortOrders,
			Map<Long, ProductionCheckViewDto> checkViewMap,
			String dateType
	) {
		Comparator<Order> combined = null;
		LinkedHashSet<String> appliedKeys = new LinkedHashSet<>();

		if (sortOrders != null) {
			for (ProductionSortOrder sortOrder : sortOrders) {
				if (sortOrder == null || sortOrder.key() == null || sortOrder.key().isBlank()) {
					continue;
				}

				String key = sortOrder.key().trim();
				if (!appliedKeys.add(key)) {
					continue;
				}

				Comparator<Order> next = buildProductionComparatorForKey(key, sortOrder.ascending(), checkViewMap);
				if (next == null) {
					appliedKeys.remove(key);
					continue;
				}

				combined = combined == null ? next : combined.thenComparing(next);
			}
		}

		// 사용자가 선택한 조건의 동률 데이터는 기존 최초 조회 순서로 안정적으로 정렬합니다.
		if (!appliedKeys.contains("checked")) {
			Comparator<Order> revisedFirst = Comparator.comparingInt(
					order -> resolveDefaultRevisionRank(order, checkViewMap)
			);
			combined = combined == null ? revisedFirst : combined.thenComparing(revisedFirst);
		}

		String defaultDateKey = "created".equalsIgnoreCase(dateType) ? "createdAt" : "preferredDeliveryDate";
		if (!("preferredDeliveryDate".equals(defaultDateKey) && appliedKeys.contains("deliveryDate"))) {
			Comparator<Order> defaultDate = "createdAt".equals(defaultDateKey)
					? compareNullable(Order::getCreatedAt, false)
					: compareNullable(Order::getPreferredDeliveryDate, false);
			combined = combined == null ? defaultDate : combined.thenComparing(defaultDate);
		}

		if (!appliedKeys.contains("id")) {
			Comparator<Order> defaultId = compareNullable(Order::getId, false);
			combined = combined == null ? defaultId : combined.thenComparing(defaultId);
		}

		return combined != null ? combined : compareNullable(Order::getId, false);
	}

	private Comparator<Order> buildProductionComparatorForKey(
			String key,
			boolean ascending,
			Map<Long, ProductionCheckViewDto> checkViewMap
	) {
		return switch (key) {
		case "id" -> compareNullable(Order::getId, ascending);
		case "productName" -> compareNullable(this::resolveProductionSortProductName, ascending);
		case "productSeries" -> compareNullable(this::resolveProductionSortProductSeries, ascending);
		case "deliveryDate" -> compareNullable(Order::getPreferredDeliveryDate, ascending);
		case "checked" -> {
			Comparator<Order> comparator = Comparator.comparingInt(
					order -> resolveFullCheckRank(order, checkViewMap)
			);
			yield ascending ? comparator : comparator.reversed();
		}
		default -> null;
		};
	}

	private <T extends Comparable<? super T>> Comparator<Order> compareNullable(
			Function<Order, T> extractor,
			boolean ascending
	) {
		Comparator<T> valueComparator = ascending
				? Comparator.nullsLast(Comparator.naturalOrder())
				: Comparator.nullsLast(Comparator.reverseOrder());

		return Comparator.comparing(extractor, valueComparator);
	}

	private String resolveProductionSortProductName(Order order) {
		OrderItem item = order != null ? order.getOrderItem() : null;
		String value = firstNonBlank(
				item != null ? item.getProductionProductName() : null,
				item != null ? item.getProductName() : null
		);
		return normalizeSortText(value);
	}

	private String resolveProductionSortProductSeries(Order order) {
		OrderItem item = order != null ? order.getOrderItem() : null;
		return normalizeSortText(item != null ? item.getProductionProductSeries() : null);
	}

	private String normalizeSortText(String value) {
		String normalized = safeText(value);
		return normalized.isBlank() ? null : normalized.toLowerCase(Locale.KOREAN);
	}

	private int resolveFullCheckRank(Order order, Map<Long, ProductionCheckViewDto> checkViewMap) {
		String state = resolveCheckStateName(order, checkViewMap);

		if (OrderCheckState.REVISED_AFTER_CHECK.name().equals(state)) {
			return 0;
		}
		if (OrderCheckState.CHECKED.name().equals(state)) {
			return 2;
		}
		return 1;
	}

	private int resolveDefaultRevisionRank(Order order, Map<Long, ProductionCheckViewDto> checkViewMap) {
		return OrderCheckState.REVISED_AFTER_CHECK.name().equals(resolveCheckStateName(order, checkViewMap)) ? 0 : 1;
	}

	private String resolveCheckStateName(Order order, Map<Long, ProductionCheckViewDto> checkViewMap) {
		if (order == null || order.getId() == null || checkViewMap == null) {
			return OrderCheckState.UNCHECKED.name();
		}

		ProductionCheckViewDto checkView = checkViewMap.get(order.getId());
		String state = checkView != null ? checkView.getCheckState() : null;
		return state == null || state.isBlank() ? OrderCheckState.UNCHECKED.name() : state;
	}

	private Map<Long, ProductionCheckViewDto> getProductionCheckViewMapInBatches(
			List<Long> orderIds,
			Member loginMember
	) {
		Map<Long, ProductionCheckViewDto> result = new LinkedHashMap<>();

		if (orderIds == null || orderIds.isEmpty()) {
			return result;
		}

		for (int from = 0; from < orderIds.size(); from += PRODUCTION_CHECK_VIEW_BATCH_SIZE) {
			int to = Math.min(from + PRODUCTION_CHECK_VIEW_BATCH_SIZE, orderIds.size());
			List<Long> batch = orderIds.subList(from, to);

			Map<Long, ProductionCheckViewDto> batchResult = orderChangeAuditService
					.getProductionCheckViewMap(batch, loginMember);

			if (batchResult != null && !batchResult.isEmpty()) {
				result.putAll(batchResult);
			}
		}

		return result;
	}

	private String normalizeKeyword(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

	private OrderStatus normalizeProductionListStatusFilter(OrderStatus statusFilter) {
	    if (statusFilter == null) {
	        return null;
	    }

	    if (PRODUCTION_LIST_VISIBLE_STATUSES.contains(statusFilter)) {
	        return statusFilter;
	    }

	    // REQUESTED, CANCELED 등 생산목록에서 보여주면 안 되는 상태는 전체 조회로 돌리되,
	    // Repository에서 visibleStatuses 4개로 한 번 더 제한합니다.
	    return null;
	}
		
	// ✅ 옵션 한줄 요약 세팅(공통화)
	private void applySingleLineOptionSummary(Page<Order> page) {
		if (page == null) {
			return;
		}
		applySingleLineOptionSummary(page.getContent());
	}

	private void applySingleLineOptionSummary(List<Order> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}

		for (Order order : orders) {
			if (order == null) {
				continue;
			}

			OrderItem item = order.getOrderItem();
			if (item == null) {
				continue;
			}

			item.setFormattedOptionText(buildOptionSummarySingleLine(order, item));

			ProductionListDisplayParts displayParts = buildProductionListDisplayParts(order, item);
			item.setProductionProductName(displayParts.productName());
			item.setProductionProductSeries(displayParts.productSeries());
			item.setProductionColor(displayParts.color());
			item.setProductionSize(displayParts.size());
			item.setProductionCategory(displayParts.category());
		}
	}
	
	private ProductionListDisplayParts buildProductionListDisplayParts(Order order, OrderItem item) {
	    Map<String, Object> optionMap = parseJsonToMap(item != null ? item.getOptionJson() : null);

	    String category = firstNonBlank(
	            pickFirstValue(optionMap, List.of("카테고리", "category", "Category")),
	            order != null && order.getProductCategory() != null ? order.getProductCategory().getName() : null
	    );

	    String productSeries = firstNonBlank(
	            pickFirstValue(optionMap, List.of(
	                    "제품시리즈",
	                    "시리즈",
	                    "중분류",
	                    "series",
	                    "Series",
	                    "productSeries",
	                    "ProductSeries"
	            ))
	    );

	    String productName = pickFirstValue(optionMap, List.of(
	            "제품명",
	            "제품",
	            "productName",
	            "ProductName",
	            "product",
	            "Product"
	    ));

	    productName = firstNonBlank(
	            productName,
	            item != null ? item.getProductName() : null
	    );

	    String colorRaw = pickFirstValue(optionMap, List.of(
	            "색상",
	            "제품색상",
	            "컬러",
	            "color",
	            "Color",
	            "productColor",
	            "ProductColor"
	    ));

	    String sizeRaw = pickFirstValue(optionMap, List.of(
	            "사이즈",
	            "제품사이즈",
	            "size",
	            "Size",
	            "productSize",
	            "ProductSize"
	    ));

	    String color = buildColorDisplay(colorRaw);
	    String size = buildSizeWithWidthMm(sizeRaw);

	    return new ProductionListDisplayParts(
	            valueOrDash(productName),
	            firstNonBlank(productSeries, "중분류없음"),
	            valueOrDash(color),
	            valueOrDash(size),
	            valueOrDash(category)
	    );
	}

	private record ProductionListDisplayParts(
	        String productName,
	        String productSeries,
	        String color,
	        String size,
	        String category
	) {
	}

	private String firstNonBlank(String... values) {
	    if (values == null || values.length == 0) {
	        return "";
	    }

	    for (String value : values) {
	        String text = safeText(value);

	        if (!text.isBlank() && !"-".equals(text)) {
	            return text;
	        }
	    }

	    return "";
	}

	private String joinNonBlank(String delimiter, String... values) {
	    if (values == null || values.length == 0) {
	        return "";
	    }

	    List<String> tokens = new ArrayList<>();

	    for (String value : values) {
	        String text = safeText(value);

	        if (!text.isBlank() && !"-".equals(text)) {
	            tokens.add(text);
	        }
	    }

	    return String.join(delimiter, tokens);
	}

	private String valueOrDash(String value) {
	    String text = safeText(value);
	    return text.isBlank() ? "-" : text;
	}
	
	@Transactional(readOnly = true)
	public boolean canAccessProductionOrderForProductionMember(Member loginMember, Order order) {
		validateProductionTeamMember(loginMember);
		return accessPolicyService.canViewProductionOrder(loginMember, order);
	}

	@Transactional(readOnly = true)
    public Map<Long, ProductionCheckViewDto> getProductionCheckViewMap(
            List<Long> orderIds,
            Member loginMember
    ) {
        validateProductionTeamMember(loginMember);
        return orderChangeAuditService.getProductionCheckViewMap(orderIds, loginMember);
    }

	@Transactional(readOnly = true)
	public List<ProductionOverviewImageDto> getProductionManagementImages(Long orderId, Member loginMember) {
	    validateProductionTeamMember(loginMember);

	    if (orderId == null) {
	        throw new IllegalArgumentException("주문 ID가 없습니다.");
	    }

	    List<Order> orders = orderRepository.findAllForProductionOverviewByIds(List.of(orderId));

	    if (orders == null || orders.isEmpty()) {
	        throw new IllegalArgumentException("해당 발주를 찾을 수 없습니다.");
	    }

	    Order order = orders.get(0);

	    if (!accessPolicyService.canViewProductionOrder(loginMember, order)) {
	        throw new AccessDeniedException("해당 발주 이미지를 조회할 권한이 없습니다.");
	    }

	    return buildManagementImageDtos(order);
	}

	@Transactional(readOnly = true)
    public List<ProductionListExcelRowDto> getProductionListExcelRowsByOrderIds(
            List<Long> orderIds,
            Member loginMember
    ) {
        validateProductionTeamMember(loginMember);

        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        List<Long> distinctIds = orderIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        if (distinctIds.isEmpty()) {
            return List.of();
        }

        List<Order> orders = orderRepository.findAllForProductionOverviewByIds(distinctIds);
        Map<Long, Order> orderMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        Map<Long, ProductionCheckViewDto> checkViewMap = orderChangeAuditService
                .getProductionCheckViewMap(distinctIds, loginMember);

        List<ProductionListExcelRowDto> result = new ArrayList<>();

        for (Long orderId : distinctIds) {
            Order order = orderMap.get(orderId);
            if (order == null || !accessPolicyService.canViewProductionOrder(loginMember, order)) {
                continue;
            }

            OrderItem item = order.getOrderItem();
            ProductionListDisplayParts displayParts = buildProductionListDisplayParts(order, item);
            int quantity = item != null ? item.getQuantity() : order.getQuantity();
            ProductionCheckViewDto checkView = checkViewMap.get(orderId);

            result.add(ProductionListExcelRowDto.builder()
                    .orderId(order.getId())
                    .companyName(resolveCompanyName(order))
                    .productName(displayParts.productName())
                    .productColor(displayParts.color())
                    .productSize(displayParts.size())
                    .quantity(quantity)
                    .adminMemo(valueOrDash(order.getAdminMemo()))
                    .preferredDeliveryDateText(order.getPreferredDeliveryDate() != null
                            ? order.getPreferredDeliveryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            : "-")
                    .categoryName(displayParts.category())
                    .checkState(checkView != null ? checkView.getCheckState() : OrderCheckState.UNCHECKED.name())
                    .checkStateLabel(checkView != null ? checkView.getCheckStateLabel() : OrderCheckState.UNCHECKED.getLabel())
                    .build());
        }

        return result;
    }

    private String resolveCompanyName(Order order) {
        try {
            if (order != null
                    && order.getTask() != null
                    && order.getTask().getRequestedBy() != null
                    && order.getTask().getRequestedBy().getCompany() != null) {
                String name = order.getTask().getRequestedBy().getCompany().getCompanyName();
                return valueOrDash(name);
            }
        } catch (Exception ignore) {
            // 지연 로딩 실패 시 대시 처리
        }
        return "-";
    }

	private List<ProductionOverviewImageDto> buildManagementImageDtos(Order order) {
	    if (order == null) {
	        return List.of();
	    }

	    List<ProductionOverviewImageDto> result = new ArrayList<>();

	    try {
	        List<OrderImage> images = order.getAdminUploadedImages();

	        if (images == null || images.isEmpty()) {
	            return List.of();
	        }

	        for (OrderImage img : images) {
	            String url = resolveAdminImageUrl(img);

	            if (isBlank(url)) {
	                continue;
	            }

	            result.add(ProductionOverviewImageDto.builder()
	                    .imageId(img.getId())
	                    .url(url)
	                    .filename(safeText(img.getFilename()))
	                    .type(safeText(img.getType()))
	                    .build());
	        }
	    } catch (Exception ignore) {
	        return List.of();
	    }

	    return result;
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
		if (orderIds == null || orderIds.isEmpty()) {
			return List.of();
		}

		List<Order> orders = orderRepository.findAllForStickerPrint(orderIds);

		if (orders == null || orders.isEmpty()) {
			return List.of();
		}

		if (allowedCategoryId != null) {
			orders = orders.stream()
					.filter(Objects::nonNull)
					.filter(o -> o.getProductCategory() != null
							&& allowedCategoryId.equals(o.getProductCategory().getId()))
					.toList();
		}

		List<StickerPrintDto> result = new ArrayList<>();

		for (Order order : orders) {
			if (order == null) {
				continue;
			}

			OrderItem orderItem = order.getOrderItem();
			Map<String, String> optionMap = parseOptionJsonToMap(
					orderItem != null ? orderItem.getOptionJson() : null
			);

			/*
			 * 스티커 데이터는 요청한 기준을 그대로 사용합니다.
			 * - 품목: optionJson["카테고리"]
			 * - 색상: optionJson["색상"]
			 * - 사이즈: optionJson["사이즈"]
			 * - 제품명: OrderItem.productName
			 */
			String category = valueOrDash(optionMap.get("카테고리"));
			String productName = orderItem != null
					? valueOrDash(orderItem.getProductName())
					: "-";
			String colorRaw = safeText(optionMap.get("색상"));
			String color = valueOrDash(colorRaw);
			String size = valueOrDash(optionMap.get("사이즈"));

			String companyName = resolveCompanyName(order);
			String deliveryDateText = formatDateTime(order.getPreferredDeliveryDate());
			String adminMemo = nvl(order.getAdminMemo()).trim();
			String firstManagementImageUrl = resolveFirstManagementImageUrl(order);

			/*
			 * 기존 DTO 필드도 함께 채워 기존 코드가 참조하더라도 깨지지 않게 유지합니다.
			 */
			String productCode = order.isStandard()
					? safeText(optionMap.get("제품코드"))
					: "";

			List<String> optionFlags = new ArrayList<>();
			addOptOrNone(optionFlags, "티슈위치", optionMap, "티슈");
			addOptOrNone(optionFlags, "드라이걸이", optionMap, "드라이");
			addOptOrNone(optionFlags, "콘센트", optionMap, "콘센트");
			addOptOrNone(optionFlags, "LED", optionMap, "LED");

			StickerPrintDto dto = StickerPrintDto.builder()
					.orderId(order.getId())
					.deliveryDateText(deliveryDateText)
					.companyName(companyName)
					.standard(order.isStandard())
					.category(category)
					.productName(productName)
					.color(color)
					.size(size)
					.adminMemo(adminMemo)
					.adminImageUrl(firstManagementImageUrl)
					.modelName(productName)
					.productCode(productCode)
					.colorDisplay(buildColorDisplay(colorRaw))
					.optionFlags(optionFlags)
					.build();

			/*
			 * 제품 한 개마다 스티커 한 장이 필요하므로 수량만큼 동일 DTO를 추가합니다.
			 * - OrderItem이 있으면 OrderItem.quantity를 우선합니다.
			 * - OrderItem이 없는 예외 데이터만 Order.quantity를 사용합니다.
			 * - 회수용 음수 수량도 실제 제품 개수 기준으로 절댓값을 사용합니다.
			 * - 수량이 0이면 출력할 실제 제품이 없으므로 스티커를 만들지 않습니다.
			 */
			int stickerQuantity = resolveStickerPrintQuantity(order, orderItem);

			for (int copyIndex = 0; copyIndex < stickerQuantity; copyIndex++) {
				result.add(dto);
			}
		}

		/*
		 * Repository 조회 순서와 관계없이 사용자가 체크한 순서대로 출력합니다.
		 * 동일 오더에서 수량만큼 복제된 스티커는 안정 정렬에 의해 서로 붙어서 유지됩니다.
		 */
		result.sort(Comparator.comparingInt(dto -> orderIds.indexOf(dto.getOrderId())));
		return result;
	}

	private int resolveStickerPrintQuantity(Order order, OrderItem orderItem) {
		int rawQuantity = orderItem != null
				? orderItem.getQuantity()
				: (order != null ? order.getQuantity() : 0);

		long absoluteQuantity = Math.abs((long) rawQuantity);

		if (absoluteQuantity > Integer.MAX_VALUE) {
			Long orderId = order != null ? order.getId() : null;
			throw new IllegalStateException(
					"스티커 출력 수량이 허용 범위를 초과했습니다. orderId=" + orderId
			);
		}

		return (int) absoluteQuantity;
	}

	/**
	 * MANAGEMENT 타입 이미지 중 가장 먼저 등록된 유효 이미지 한 장을 반환합니다.
	 *
	 * Order.orderImages에는 @OrderBy가 없으므로 List의 우연한 조회 순서에 의존하지 않고,
	 * DB 식별자(id) 오름차순을 "첫 번째" 기준으로 사용합니다.
	 */
	private String resolveFirstManagementImageUrl(Order order) {
		if (order == null) {
			return "";
		}

		try {
			List<OrderImage> managementImages = order.getAdminUploadedImages();

			if (managementImages == null || managementImages.isEmpty()) {
				return "";
			}

			return managementImages.stream()
					.filter(Objects::nonNull)
					.sorted(Comparator.comparing(
							OrderImage::getId,
							Comparator.nullsLast(Comparator.naturalOrder())
					))
					.map(this::resolveAdminImageUrl)
					.filter(url -> !isBlank(url))
					.findFirst()
					.orElse("");

		} catch (Exception ignore) {
			return "";
		}
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

	private void addOptOrNone(
	        List<String> out,
	        String key,
	        Map<String, String> map,
	        String... fallbackKeywords
	) {
	    if (out == null) {
	        return;
	    }

	    // 1) 기존 방식: 정확한 key 우선 조회
	    String directValue = getDirectOptionValue(map, key);

	    if (!isBlank(directValue)) {
	        out.add(key + ": " + directValue);
	        return;
	    }

	    // 2) 확장 방식: key가 없으면 전체 value에서 키워드 포함 여부 조회
	    String matchedValue = findFirstPositiveValueContainsAnyKeyword(map, fallbackKeywords);

	    if (!isBlank(matchedValue)) {
	        out.add(key + ": " + matchedValue);
	        return;
	    }

	    // 3) 둘 다 없으면 없음
	    out.add(key + ": 없음");
	}

	private String getDirectOptionValue(Map<String, String> map, String key) {
	    if (map == null || map.isEmpty() || isBlank(key)) {
	        return "";
	    }

	    String value = nvl(map.get(key)).trim();

	    if (isBlank(value)) {
	        return "";
	    }

	    if (isNoneOptionValue(value)) {
	        return "없음";
	    }

	    return value;
	}

	private String findFirstPositiveValueContainsAnyKeyword(Map<String, String> map, String... keywords) {
	    if (map == null || map.isEmpty() || keywords == null || keywords.length == 0) {
	        return "";
	    }

	    for (Map.Entry<String, String> entry : map.entrySet()) {
	        String value = nvl(entry.getValue()).trim();

	        if (isBlank(value)) {
	            continue;
	        }

	        // "드라이걸이 추가안함", "콘센트 없음" 같은 값은 선택된 옵션으로 보지 않음
	        if (isNoneOptionValue(value)) {
	            continue;
	        }

	        for (String keyword : keywords) {
	            if (isBlank(keyword)) {
	                continue;
	            }

	            if (containsIgnoreCase(value, keyword)) {
	                return value;
	            }
	        }
	    }

	    return "";
	}

	private boolean containsIgnoreCase(String text, String keyword) {
	    if (text == null || keyword == null) {
	        return false;
	    }

	    return text.toLowerCase().contains(keyword.toLowerCase());
	}

	private boolean isNoneOptionValue(String value) {
	    if (isBlank(value)) {
	        return true;
	    }

	    String v = value.trim().toLowerCase();

	    return v.equals("없음")
	            || v.equals("없다")
	            || v.equals("무")
	            || v.equals("x")
	            || v.equals("no")
	            || v.equals("n")
	            || v.equals("false")
	            || v.contains("없음")
	            || v.contains("추가안함")
	            || v.contains("추가 안함")
	            || v.contains("선택안함")
	            || v.contains("선택 안함")
	            || v.contains("미선택")
	            || v.contains("해당없음")
	            || v.contains("해당 없음");
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
		String url = img.getUrl();
		return url == null ? "" : url.trim();
	}
	
	@Transactional(readOnly = true)
	public Map<Long, List<ProductionOverviewFieldDto>> buildProductionOverviewBriefFieldMap(List<Order> orders) {
	    Map<Long, List<ProductionOverviewFieldDto>> result = new LinkedHashMap<>();

	    if (orders == null || orders.isEmpty()) {
	        return result;
	    }

	    for (Order order : orders) {
	        result.put(order.getId(), buildProductionOverviewFields(order, true));
	    }

	    return result;
	}

	/**
	 * 생산 상세 페이지에서 일괄보기와 동일한 기준의 전체 상세 필드를 사용합니다.
	 * briefMode를 사용하지 않으므로 주소, 메모, 담당직원, 전체 옵션이 누락되지 않습니다.
	 */
	@Transactional(readOnly = true)
	public List<ProductionOverviewFieldDto> buildProductionOverviewDetailFields(Order order) {
	    return buildProductionOverviewFields(order, false);
	}

	/**
	 * Task.managedBy 기준 발주 관리 담당직원명입니다.
	 * 생산담당자(Order.assignedProductionHandler)와 혼동하지 않도록 이 메서드로 통일합니다.
	 */
	@Transactional(readOnly = true)
	public String resolveProductionManagedByName(Order order) {
	    if (order == null || order.getTask() == null || order.getTask().getManagedBy() == null) {
	        return "미배정";
	    }

	    String name = safeText(order.getTask().getManagedBy().getName());
	    return name.isBlank() ? "미배정" : name;
	}

	@Transactional(readOnly = true)
	public List<ProductionOverviewOrderDto> getProductionOverviewOrders(List<Long> orderIds, Member loginMember) {
	    validateProductionTeamMember(loginMember);

	    if (orderIds == null || orderIds.isEmpty()) {
	        return List.of();
	    }

	    List<Long> distinctIds = orderIds.stream()
	            .filter(Objects::nonNull)
	            .collect(Collectors.toCollection(LinkedHashSet::new))
	            .stream()
	            .toList();

	    if (distinctIds.isEmpty()) {
	        return List.of();
	    }

	    List<Order> orders = orderRepository.findAllForProductionOverviewDataByIds(distinctIds);
        Map<Long, ProductionCheckViewDto> checkViewMap = orderChangeAuditService
                .getProductionCheckViewMap(distinctIds, loginMember);

	    Map<Long, Order> orderMap = orders.stream()
	            .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a, LinkedHashMap::new));

	    List<ProductionOverviewOrderDto> result = new ArrayList<>();

	    for (Long orderId : distinctIds) {
	        Order order = orderMap.get(orderId);

	        if (order == null) {
	            continue;
	        }

	        if (!accessPolicyService.canViewProductionOrder(loginMember, order)) {
	            continue;
	        }

	        result.add(toProductionOverviewOrderDto(order, loginMember, checkViewMap.get(orderId)));
	    }

	    return result;
	}

	@Transactional
	public ProductionOverviewCompleteResponse completeProductionOrderFromOverview(Long orderId, Member loginMember) {
	    validateProductionTeamMember(loginMember);

	    if (orderId == null) {
	        throw new IllegalArgumentException("주문 ID가 없습니다.");
	    }

	    if (isCuttingProductionMember(loginMember)) {
	        throw new AccessDeniedException("재단 직원은 생산완료 처리를 할 수 없습니다.");
	    }

	    Order order = orderRepository.findByIdForProductionStatusUpdate(orderId)
	            .orElseThrow(() -> new IllegalArgumentException("해당 발주를 찾을 수 없습니다."));

	    if (!accessPolicyService.canOperateProductionOrder(loginMember, order)) {
	        throw new AccessDeniedException("자신의 생산 카테고리 또는 거울·LED거울 공통 작업 그룹의 발주만 생산완료 처리할 수 있습니다.");
	    }

	    if (order.getStatus() != OrderStatus.CONFIRMED) {
	        throw new IllegalStateException("승인 완료 상태의 발주만 생산완료 처리할 수 있습니다.");
	    }

	    int updated = orderRepository.updateProductionStatusIfCurrentStatus(
	            orderId,
	            OrderStatus.CONFIRMED,
	            OrderStatus.PRODUCTION_DONE,
	            LocalDateTime.now()
	    );

	    if (updated != 1) {
            throw new IllegalStateException("이미 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요.");
        }

        orderChangeAuditService.recordOrderChange(
                order,
                OrderChangeSourceArea.PRODUCTION,
                loginMember.getId(),
                loginMember.getUsername(),
                resolveCheckedByUsername(loginMember),
                "PRODUCTION_COMPLETE",
                "생산완료 처리",
                "/team/productionList/" + orderId + "/complete",
                List.of(OrderFieldChangeCommand.of(
                        "status",
                        "오더 상태",
                        OrderStatus.CONFIRMED.getLabel(),
                        OrderStatus.PRODUCTION_DONE.getLabel(),
                        OrderWorkArea.DISPATCH,
                        OrderWorkArea.DELIVERY
                ))
        );

	    return ProductionOverviewCompleteResponse.builder()
	            .orderId(orderId)
	            .status(OrderStatus.PRODUCTION_DONE.name())
	            .statusLabel(OrderStatus.PRODUCTION_DONE.getLabel())
	            .message("생산완료 처리되었습니다.")
	            .build();
	}

	private ProductionOverviewOrderDto toProductionOverviewOrderDto(
            Order order,
            Member loginMember,
            ProductionCheckViewDto checkView
    ) {
	    OrderItem item = order.getOrderItem();

	    String companyName = "-";
	    try {
	        if (order.getTask() != null
	                && order.getTask().getRequestedBy() != null
	                && order.getTask().getRequestedBy().getCompany() != null
	                && order.getTask().getRequestedBy().getCompany().getCompanyName() != null
	                && !order.getTask().getRequestedBy().getCompany().getCompanyName().isBlank()) {

	            companyName = order.getTask().getRequestedBy().getCompany().getCompanyName();
	        }
	    } catch (Exception ignore) {
	        companyName = "-";
	    }

	    // 대량 overview 응답에서는 이미지를 의도적으로 제외합니다.
	    // 각 화면이 현재 필요한 주문의 이미지만 /productionList/{orderId}/management-images 로 지연 조회합니다.
	    List<ProductionOverviewImageDto> adminImages = List.of();

	    OrderStatus status = order.getStatus();
        String checkStateName = checkView != null ? checkView.getCheckState() : OrderCheckState.UNCHECKED.name();
        String checkStateLabel = checkView != null ? checkView.getCheckStateLabel() : OrderCheckState.UNCHECKED.getLabel();
        boolean checked = OrderCheckState.CHECKED.name().equals(checkStateName);

	    return ProductionOverviewOrderDto.builder()
	            .orderId(order.getId())
	            .status(status != null ? status.name() : "")
	            .statusLabel(status != null ? status.getLabel() : "-")
	            .canComplete(status == OrderStatus.CONFIRMED && accessPolicyService.canOperateProductionOrder(loginMember, order))
            .canRequestAdmin(accessPolicyService.canOperateProductionOrder(loginMember, order))
	            .companyName(companyName)
	            .productName(item != null ? safeText(item.getProductName()) : "-")
	            .categoryName(order.getProductCategory() != null ? safeText(order.getProductCategory().getName()) : "-")
	            .standardLabel(order.isStandard() ? "규격" : "비규격")
	            .quantity(item != null ? item.getQuantity() : order.getQuantity())
	            .createdDateText(formatDateTime(order.getCreatedAt()))
	            .preferredDeliveryDateText(formatDateTime(order.getPreferredDeliveryDate()))
	            .orderComment(safeText(order.getOrderComment()))
	            .adminMemo(safeText(order.getAdminMemo()))
	            .fields(buildProductionOverviewFields(order, false))
	            .adminImages(adminImages)
	            .checked(checked)
	            .checkState(checkStateName)
                .checkStateLabel(checkStateLabel)
                .checkedByUsername(checkView != null ? safeText(checkView.getCheckedByUsername()) : "")
                .checkedAtText(checkView != null ? safeText(checkView.getCheckedAtText()) : "")
                .revisionMarkedByUsername(checkView != null ? safeText(checkView.getRevisionMarkedByUsername()) : "")
                .revisionMarkedAtText(checkView != null ? safeText(checkView.getRevisionMarkedAtText()) : "")
                .revisionReason(checkView != null ? safeText(checkView.getRevisionReason()) : "")
                .revisionCount(checkView != null ? checkView.getRevisionCount() : 0)
	            .build();
	}

	private List<ProductionOverviewFieldDto> buildProductionOverviewFields(Order order, boolean briefMode) {
	    List<ProductionOverviewFieldDto> fields = new ArrayList<>();

	    if (order == null) {
	        return fields;
	    }

	    OrderItem item = order.getOrderItem();

	    fields.add(ProductionOverviewFieldDto.important(
	            "제품명",
	            item != null ? safeText(item.getProductName()) : "-"
	    ));

	    fields.add(ProductionOverviewFieldDto.of(
	            "수량",
	            String.valueOf(item != null ? item.getQuantity() : order.getQuantity())
	    ));

	    fields.add(ProductionOverviewFieldDto.of(
	            "제품분류",
	            order.getProductCategory() != null ? safeText(order.getProductCategory().getName()) : "-"
	    ));

	    fields.add(ProductionOverviewFieldDto.of(
	            "규격여부",
	            order.isStandard() ? "규격" : "비규격"
	    ));

	    fields.add(ProductionOverviewFieldDto.important(
	            "담당직원",
	            resolveProductionManagedByName(order)
	    ));

	    String address = buildProductionAddress(order);
	    if (!isBlank(address)) {
	        fields.add(ProductionOverviewFieldDto.of("주소", address));
	    }

	    if (!isBlank(order.getOrderComment())) {
	        fields.add(ProductionOverviewFieldDto.of("발주메모", safeText(order.getOrderComment())));
	    }

	    if (!isBlank(order.getAdminMemo())) {
	        fields.add(ProductionOverviewFieldDto.of("관리자메모", safeText(order.getAdminMemo())));
	    }

	    Map<String, Object> optionMap = parseJsonToMap(item != null ? item.getOptionJson() : null);

	    for (Map.Entry<String, Object> entry : optionMap.entrySet()) {
	        String label = safeText(entry.getKey());
	        String value = safeText(flattenOptionValue(entry.getValue()));

	        if (isBlank(label) || isBlank(value)) {
	            continue;
	        }

	        fields.add(ProductionOverviewFieldDto.of(label, value));
	    }

	    if (briefMode && fields.size() > 12) {
	        return new ArrayList<>(fields.subList(0, 12));
	    }

	    return fields;
	}

	@SuppressWarnings("unchecked")
	private String flattenOptionValue(Object value) {
	    if (value == null) {
	        return "";
	    }

	    if (value instanceof Map<?, ?> mapValue) {
	        List<String> tokens = new ArrayList<>();

	        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
	            String k = safeText(entry.getKey());
	            String v = flattenOptionValue(entry.getValue());

	            if (!isBlank(k) && !isBlank(v)) {
	                tokens.add(k + ": " + v);
	            } else if (!isBlank(v)) {
	                tokens.add(v);
	            }
	        }

	        return String.join(" / ", tokens);
	    }

	    if (value instanceof List<?> listValue) {
	        return listValue.stream()
	                .map(this::flattenOptionValue)
	                .filter(v -> v != null && !v.isBlank())
	                .collect(Collectors.joining(" / "));
	    }

	    return safeText(value);
	}

	private String buildProductionAddress(Order order) {
	    if (order == null) {
	        return "";
	    }

	    List<String> tokens = new ArrayList<>();

	    if (!isBlank(order.getRoadAddress())) {
	        tokens.add(safeText(order.getRoadAddress()));
	    }

	    if (!isBlank(order.getDetailAddress())) {
	        tokens.add(safeText(order.getDetailAddress()));
	    }

	    String region = String.join(" ",
	            List.of(
	                    safeText(order.getDoName()),
	                    safeText(order.getSiName()),
	                    safeText(order.getGuName())
	            ).stream().filter(v -> !v.isBlank()).toList()
	    );

	    if (!isBlank(region)) {
	        tokens.add(region);
	    }

	    if (!isBlank(order.getZipCode())) {
	        tokens.add("(" + safeText(order.getZipCode()) + ")");
	    }

	    return String.join(" ", tokens);
	}

	private String formatDateTime(LocalDateTime dateTime) {
	    if (dateTime == null) {
	        return "-";
	    }

	    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
	}

	private void validateProductionTeamMember(Member member) {
	    if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
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

	    return "재단".equals(categoryName) || isMirrorCuttingProductionMember(member);
	}

	private boolean isMirrorCuttingProductionMember(Member member) {
	    if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
	        return false;
	    }

	    if (member.getTeamCategory() == null) {
	        return false;
	    }

	    Long categoryId = member.getTeamCategory().getId();
	    String categoryName = member.getTeamCategory().getName();

	    return Objects.equals(MIRROR_CUTTING_TEAM_CATEGORY_ID, categoryId)
	            && MIRROR_CUTTING_TEAM_CATEGORY_NAME.equals(categoryName);
	}

	private boolean canAccessMirrorCuttingOrders(Member member) {
	    if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
	        return false;
	    }

	    if (member.getTeamCategory() == null || member.getTeamCategory().getName() == null) {
	        return false;
	    }

	    return MIRROR_CUTTING_ACCESS_TEAM_CATEGORY_NAMES.contains(member.getTeamCategory().getName().trim());
	}
}