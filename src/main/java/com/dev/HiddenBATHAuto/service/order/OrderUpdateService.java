package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderUpdateService {

	private static final String ADMIN_IMAGE_TYPE = "MANAGEMENT";
	private static final String SITE_DELIVERY_METHOD_NAME = "현장배송";

	private static final Set<String> REQUIRED_DELIVERY_HANDLER_METHOD_NAMES = Set.of("직배송", "화물", "현장배송");

	private static final Set<String> OPTION_VALUE_FIXED_KEYS = Set.of("카테고리", "제품시리즈", "제품시리즈ID");

	private static final Set<String> OPTION_DELETE_BLOCKED_KEYS = Set.of("카테고리", "제품시리즈", "제품시리즈ID", "제품명", "사이즈",
			"색상");

	private final OrderRepository orderRepository;
	private final DeliveryMethodRepository deliveryMethodRepository;
	private final MemberRepository memberRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final CompanyRepository companyRepository;
	private final TaskRepository taskRepository;
	private final DeliveryOrderIndexService deliveryOrderIndexService;
	private final OrderImageRepository orderImageRepository;
	private final ObjectMapper objectMapper;
	private final OrderCheckStatusService orderCheckStatusService;

	@Value("${spring.upload.path}")
	private String uploadRootPath;

	/**
	 * 기존 호출부 보호용 오버로드입니다. 기존 화면에서는 현장주소 input이 없으므로 DB에 저장된 현장주소를 그대로 유지합니다.
	 */
	@Transactional
	public void updateOrder(Long orderId, int productCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, List<Long> deleteAdminImageIds,
			List<MultipartFile> adminImages, String adminMemo) {
		Order order = getOrderOrThrow(orderId);

		updateOrder(orderId, productCost, order.getQuantity(), order.getSupplyPrice(), order.getTotalAmount(),
				order.getPackingCost(), order.getDeliveryCost(), preferredDeliveryDate, statusStr, deliveryMethodId,
				deliveryHandlerId, productCategoryId, companyId, requesterMemberId, order.getZipCode(),
				order.getDoName(), order.getSiName(), order.getGuName(), order.getRoadAddress(),
				order.getDetailAddress(), order.getSiteZipCode(), order.getSiteDoName(), order.getSiteSiName(),
				order.getSiteGuName(), order.getSiteRoadAddress(), order.getSiteDetailAddress(), order.getOrdererName(),
				order.getOrdererPhone(), order.getOrderItem() != null ? order.getOrderItem().getOptionJson() : null,
				deleteAdminImageIds, adminImages, adminMemo, null, false, null);
	}

	@Transactional
	public void updateOrder(Long orderId, int productCost, int quantity, int supplyPrice, int totalAmount,
			int packingCost, int deliveryCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String ordererName, String ordererPhone,
			String optionJson, List<Long> deleteAdminImageIds, List<MultipartFile> adminImages, String adminMemo) {
		Order order = getOrderOrThrow(orderId);
		updateOrder(orderId, productCost, quantity, supplyPrice, totalAmount, packingCost, deliveryCost,
				preferredDeliveryDate, statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId, companyId,
				requesterMemberId, zipCode, doName, siName, guName, roadAddress, detailAddress, order.getSiteZipCode(),
				order.getSiteDoName(), order.getSiteSiName(), order.getSiteGuName(), order.getSiteRoadAddress(),
				order.getSiteDetailAddress(), ordererName, ordererPhone, optionJson, deleteAdminImageIds, adminImages,
				adminMemo, null, false, null);
	}

	@Transactional
	public void updateOrder(Long orderId, int productCost, int quantity, int supplyPrice, int totalAmount,
			int packingCost, int deliveryCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String ordererName, String ordererPhone,
			String optionJson, List<Long> deleteAdminImageIds, List<MultipartFile> adminImages, String adminMemo,
			String updatedByUsername) {
		Order order = getOrderOrThrow(orderId);
		updateOrder(orderId, productCost, quantity, supplyPrice, totalAmount, packingCost, deliveryCost,
				preferredDeliveryDate, statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId, companyId,
				requesterMemberId, zipCode, doName, siName, guName, roadAddress, detailAddress, order.getSiteZipCode(),
				order.getSiteDoName(), order.getSiteSiName(), order.getSiteGuName(), order.getSiteRoadAddress(),
				order.getSiteDetailAddress(), ordererName, ordererPhone, optionJson, deleteAdminImageIds, adminImages,
				adminMemo, null, false, updatedByUsername);
	}

	/**
	 * 기존 상세 화면 호환용입니다. dispatchCompleteMessageSubmitted가 true일 때만 출고완료 메시지를 수정합니다.
	 */
	@Transactional
	public void updateOrder(Long orderId, int productCost, int quantity, int supplyPrice, int totalAmount,
			int packingCost, int deliveryCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String ordererName, String ordererPhone,
			String optionJson, List<Long> deleteAdminImageIds, List<MultipartFile> adminImages, String adminMemo,
			String dispatchCompleteMessage, boolean dispatchCompleteMessageSubmitted, String updatedByUsername) {
		Order order = getOrderOrThrow(orderId);
		updateOrder(orderId, productCost, quantity, supplyPrice, totalAmount, packingCost, deliveryCost,
				preferredDeliveryDate, statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId, companyId,
				requesterMemberId, zipCode, doName, siName, guName, roadAddress, detailAddress, order.getSiteZipCode(),
				order.getSiteDoName(), order.getSiteSiName(), order.getSiteGuName(), order.getSiteRoadAddress(),
				order.getSiteDetailAddress(), ordererName, ordererPhone, optionJson, deleteAdminImageIds, adminImages,
				adminMemo, dispatchCompleteMessage, dispatchCompleteMessageSubmitted, updatedByUsername);
	}

	/**
	 * 현장주소까지 함께 수정하는 기존 호출부 보호용 메서드입니다.
	 * 거울 재단 상품 여부는 기존 DB 값을 유지합니다.
	 */
	@Transactional
	public void updateOrder(Long orderId, int productCost, int quantity, int supplyPrice, int totalAmount,
			int packingCost, int deliveryCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String siteZipCode, String siteDoName,
			String siteSiName, String siteGuName, String siteRoadAddress, String siteDetailAddress, String ordererName,
			String ordererPhone, String optionJson, List<Long> deleteAdminImageIds, List<MultipartFile> adminImages,
			String adminMemo, String dispatchCompleteMessage, boolean dispatchCompleteMessageSubmitted,
			String updatedByUsername) {
		Order order = getOrderOrThrow(orderId);

		updateOrder(orderId, productCost, quantity, supplyPrice, totalAmount, packingCost, deliveryCost,
				preferredDeliveryDate, statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId, companyId,
				requesterMemberId, zipCode, doName, siName, guName, roadAddress, detailAddress, siteZipCode,
				siteDoName, siteSiName, siteGuName, siteRoadAddress, siteDetailAddress, ordererName, ordererPhone,
				optionJson, deleteAdminImageIds, adminImages, adminMemo, dispatchCompleteMessage,
				dispatchCompleteMessageSubmitted, updatedByUsername, order.isMirrorCuttingProduct());
	}

	/**
	 * 현장주소와 거울 재단 상품 여부까지 함께 수정하는 실제 처리 메서드입니다.
	 */
	@Transactional
	public void updateOrder(Long orderId, int productCost, int quantity, int supplyPrice, int totalAmount,
			int packingCost, int deliveryCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String siteZipCode, String siteDoName,
			String siteSiName, String siteGuName, String siteRoadAddress, String siteDetailAddress, String ordererName,
			String ordererPhone, String optionJson, List<Long> deleteAdminImageIds, List<MultipartFile> adminImages,
			String adminMemo, String dispatchCompleteMessage, boolean dispatchCompleteMessageSubmitted,
			String updatedByUsername, boolean mirrorCuttingProduct) {
		Order order = getOrderOrThrow(orderId);

		ProductionVisibleOrderSnapshot beforeSnapshot = ProductionVisibleOrderSnapshot.from(order);
		boolean adminImageChanged = hasDeleteIds(deleteAdminImageIds) || hasUploadFiles(adminImages);

		OrderStatus status = parseOrderStatus(statusStr);
		MoneySnapshot moneySnapshot = normalizeMoney(productCost, quantity, supplyPrice, totalAmount);

		order.setProductCost(moneySnapshot.productCost());
		order.setQuantity(moneySnapshot.quantity());
		order.setSupplyPrice(moneySnapshot.supplyPrice());
		order.setTotalAmount(moneySnapshot.totalAmount());
		order.setPackingCost(nonNegative(packingCost, "포장비"));
		order.setDeliveryCost(nonNegative(deliveryCost, "배송비"));
		order.setMirrorCuttingProduct(mirrorCuttingProduct);

		if (order.getOrderItem() != null) {
			order.getOrderItem().setQuantity(moneySnapshot.quantity());
		}

		order.setPreferredDeliveryDate(preferredDeliveryDate != null ? preferredDeliveryDate.atStartOfDay() : null);
		order.setStatus(status);

		normalizeId(deliveryMethodId).ifPresentOrElse(id -> {
			var method = deliveryMethodRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId. id=" + id));
			order.setDeliveryMethod(method);
		}, () -> order.setDeliveryMethod(null));

		boolean canceled = status == OrderStatus.CANCELED;
		boolean siteDelivery = isSiteDeliveryMethod(order);
		boolean handlerRequired = isDeliveryHandlerRequired(order);

		applyDeliveryAddress(order, siteDelivery, zipCode, doName, siName, guName, roadAddress, detailAddress,
				siteZipCode, siteDoName, siteSiName, siteGuName, siteRoadAddress, siteDetailAddress);

		applyDeliveryHandler(order, preferredDeliveryDate, deliveryHandlerId, canceled, handlerRequired);

		normalizeId(productCategoryId).ifPresentOrElse(id -> {
			var category = teamCategoryRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId. id=" + id));
			order.setProductCategory(category);
		}, () -> order.setProductCategory(null));

		updateRequesterIfNeeded(order, normalizeId(companyId), normalizeId(requesterMemberId));
		updateOrderItemOptionJson(order, optionJson);

		order.setOrdererName(normalizeNullableText(ordererName));
		order.setOrdererPhone(normalizeNullableText(ordererPhone));
		order.setAdminMemo(normalizeNullableText(adminMemo));

		if (dispatchCompleteMessageSubmitted) {
			order.setDispatchCompleteMessage(normalizeNullableText(dispatchCompleteMessage));
		}

		order.setUpdatedAt(LocalDateTime.now());

		deleteAdminImages(order, deleteAdminImageIds);
		saveAdminImages(order, adminImages);

		if (canceled || order.getAssignedDeliveryHandler() == null) {
			deliveryOrderIndexService.removeIndex(order);
		} else {
			deliveryOrderIndexService.ensureIndex(order);
		}

		ProductionVisibleOrderSnapshot afterSnapshot = ProductionVisibleOrderSnapshot.from(order);
		boolean productionVisibleChanged = adminImageChanged || !Objects.equals(beforeSnapshot, afterSnapshot);

		orderCheckStatusService.markRevisedAfterProductionCheckIfNeeded(order, updatedByUsername,
				productionVisibleChanged,
				buildProductionRevisionReason(beforeSnapshot, afterSnapshot, adminImageChanged));
	}

	private Order getOrderOrThrow(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));
	}

	private void applyDeliveryAddress(Order order, boolean siteDelivery, String zipCode, String doName, String siName,
			String guName, String roadAddress, String detailAddress, String siteZipCode, String siteDoName,
			String siteSiName, String siteGuName, String siteRoadAddress, String siteDetailAddress) {
		String resolvedZipCode = firstNotBlank(zipCode, order.getZipCode());
		String resolvedDoName = firstNotBlank(doName, order.getDoName());
		String resolvedSiName = firstNotBlank(siName, order.getSiName());
		String resolvedGuName = firstNotBlank(guName, order.getGuName());
		String resolvedRoadAddress = firstNotBlank(roadAddress, order.getRoadAddress());
		String resolvedDetailAddress = firstNotBlank(detailAddress, order.getDetailAddress());

		order.setZipCode(resolvedZipCode);
		order.setDoName(resolvedDoName);
		order.setSiName(resolvedSiName);
		order.setGuName(resolvedGuName);
		order.setRoadAddress(resolvedRoadAddress);
		order.setDetailAddress(resolvedDetailAddress);

		if (siteDelivery) {
			String resolvedSiteZipCode = firstNotBlank(siteZipCode, order.getSiteZipCode());
			String resolvedSiteDoName = firstNotBlank(siteDoName, order.getSiteDoName());
			String resolvedSiteSiName = firstNotBlank(siteSiName, order.getSiteSiName());
			String resolvedSiteGuName = firstNotBlank(siteGuName, order.getSiteGuName());
			String resolvedSiteRoadAddress = firstNotBlank(siteRoadAddress, order.getSiteRoadAddress());
			String resolvedSiteDetailAddress = firstNotBlank(siteDetailAddress, order.getSiteDetailAddress());

			if (resolvedSiteRoadAddress == null) {
				throw new IllegalArgumentException("현장배송 선택 시 현장주소는 필수입니다.");
			}

			order.setSiteZipCode(resolvedSiteZipCode);
			order.setSiteDoName(resolvedSiteDoName);
			order.setSiteSiName(resolvedSiteSiName);
			order.setSiteGuName(resolvedSiteGuName);
			order.setSiteRoadAddress(resolvedSiteRoadAddress);
			order.setSiteDetailAddress(resolvedSiteDetailAddress);
			return;
		}

		if (resolvedRoadAddress == null) {
			throw new IllegalArgumentException("현장배송이 아닌 경우 배송주소는 필수입니다.");
		}

		clearSiteAddress(order);
	}

	private String normalizeNullableText(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void clearSiteAddress(Order order) {
		order.setSiteZipCode(null);
		order.setSiteDoName(null);
		order.setSiteSiName(null);
		order.setSiteGuName(null);
		order.setSiteRoadAddress(null);
		order.setSiteDetailAddress(null);
	}

	private void applyDeliveryHandler(Order order, LocalDate preferredDeliveryDate, Optional<Long> deliveryHandlerId,
			boolean canceled, boolean handlerRequired) {
		if (canceled) {
			order.setAssignedDeliveryHandler(null);
			return;
		}

		Optional<Long> normalizedHandlerId = normalizeId(deliveryHandlerId);
		String methodName = getDeliveryMethodName(order).orElse("배송수단");

		if (handlerRequired && normalizedHandlerId.isEmpty()) {
			throw new IllegalArgumentException(methodName + " 선택 시 배송팀 담당자는 필수입니다.");
		}

		if (normalizedHandlerId.isEmpty()) {
			order.setAssignedDeliveryHandler(null);
			return;
		}

		if (preferredDeliveryDate == null) {
			throw new IllegalArgumentException("배송팀 담당자를 지정하는 경우 배송희망일은 필수입니다.");
		}

		Long handlerId = normalizedHandlerId.get();
		Member member = memberRepository.findById(handlerId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId. id=" + handlerId));

		order.setAssignedDeliveryHandler(member);
	}

	private boolean hasDeleteIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return false;
		}
		return ids.stream().anyMatch(id -> id != null && id > 0);
	}

	private boolean hasUploadFiles(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			return false;
		}
		return files.stream().anyMatch(file -> file != null && !file.isEmpty());
	}

	private String buildProductionRevisionReason(ProductionVisibleOrderSnapshot before,
			ProductionVisibleOrderSnapshot after, boolean adminImageChanged) {
		List<String> reasons = new ArrayList<>();

		if (!Objects.equals(before.statusName(), after.statusName()))
			reasons.add("발주상태");
		if (!Objects.equals(before.productCategoryId(), after.productCategoryId()))
			reasons.add("생산팀 카테고리");
		if (before.mirrorCuttingProduct() != after.mirrorCuttingProduct())
			reasons.add("거울 재단 상품 여부");
		if (!Objects.equals(before.preferredDeliveryDate(), after.preferredDeliveryDate()))
			reasons.add("배송희망일");
		if (!Objects.equals(before.deliveryMethodId(), after.deliveryMethodId()))
			reasons.add("배송수단");
		if (!Objects.equals(before.assignedDeliveryHandlerId(), after.assignedDeliveryHandlerId()))
			reasons.add("배송담당자");
		if (!Objects.equals(before.deliveryAddress(), after.deliveryAddress()))
			reasons.add("배송주소");
		if (!Objects.equals(before.siteAddress(), after.siteAddress()))
			reasons.add("현장주소");
		if (before.quantity() != after.quantity())
			reasons.add("수량");
		if (!Objects.equals(before.adminMemo(), after.adminMemo()))
			reasons.add("관리자 남김말");
		if (!Objects.equals(before.optionJson(), after.optionJson()))
			reasons.add("제품 옵션");
		if (!Objects.equals(before.orderItemProductName(), after.orderItemProductName()))
			reasons.add("제품명");
		if (adminImageChanged)
			reasons.add("관리자 첨부파일");

		if (reasons.isEmpty()) {
			return "관리자 수정";
		}

		return String.join(", ", reasons) + " 수정";
	}

	private record ProductionVisibleOrderSnapshot(String statusName, Long productCategoryId,
			boolean mirrorCuttingProduct, String preferredDeliveryDate, Long deliveryMethodId,
			Long assignedDeliveryHandlerId, String deliveryAddress, String siteAddress, int quantity, String adminMemo,
			String optionJson, String orderItemProductName) {
		static ProductionVisibleOrderSnapshot from(Order order) {
			if (order == null) {
				return new ProductionVisibleOrderSnapshot(null, null, false, null, null, null, null, null, 0, null, null,
						null);
			}

			OrderItem orderItem = order.getOrderItem();

			return new ProductionVisibleOrderSnapshot(order.getStatus() != null ? order.getStatus().name() : null,
					order.getProductCategory() != null ? order.getProductCategory().getId() : null,
					order.isMirrorCuttingProduct(),
					order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : null,
					order.getDeliveryMethod() != null ? order.getDeliveryMethod().getId() : null,
					order.getAssignedDeliveryHandler() != null ? order.getAssignedDeliveryHandler().getId() : null,
					join(order.getZipCode(), order.getDoName(), order.getSiName(), order.getGuName(),
							order.getRoadAddress(), order.getDetailAddress()),
					join(order.getSiteZipCode(), order.getSiteDoName(), order.getSiteSiName(), order.getSiteGuName(),
							order.getSiteRoadAddress(), order.getSiteDetailAddress()),
					order.getQuantity(), normalize(order.getAdminMemo()),
					orderItem != null ? normalize(orderItem.getOptionJson()) : null,
					orderItem != null ? normalize(orderItem.getProductName()) : null);
		}

		private static String join(String... values) {
			if (values == null) {
				return null;
			}
			String joined = java.util.Arrays.stream(values).map(ProductionVisibleOrderSnapshot::normalize)
					.filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));
			return joined.isBlank() ? null : joined;
		}

		private static String normalize(String value) {
			if (value == null) {
				return null;
			}
			String trimmed = value.trim();
			return trimmed.isEmpty() ? null : trimmed;
		}
	}

	private Optional<Long> normalizeId(Optional<Long> id) {
		if (id == null || id.isEmpty()) {
			return Optional.empty();
		}

		Long value = id.get();
		if (value == null || value <= 0) {
			return Optional.empty();
		}

		return Optional.of(value);
	}

	private boolean isSiteDeliveryMethod(Order order) {
		return getDeliveryMethodName(order).map(SITE_DELIVERY_METHOD_NAME::equals).orElse(false);
	}

	private boolean isDeliveryHandlerRequired(Order order) {
		return getDeliveryMethodName(order).map(REQUIRED_DELIVERY_HANDLER_METHOD_NAMES::contains).orElse(false);
	}

	private Optional<String> getDeliveryMethodName(Order order) {
		if (order == null || order.getDeliveryMethod() == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(normalizeNullableText(order.getDeliveryMethod().getMethodName()));
	}

	private MoneySnapshot normalizeMoney(int productCost, int quantity, int supplyPrice, int totalAmount) {
		int normalizedProductCost = Math.max(0, productCost);
		int normalizedQuantity = Math.max(0, quantity);
		int normalizedSupplyPrice = Math.max(0, supplyPrice);
		int normalizedTotalAmount = Math.max(0, totalAmount);

		if (normalizedSupplyPrice == 0 && normalizedProductCost > 0 && normalizedQuantity > 0) {
			normalizedSupplyPrice = normalizedProductCost * normalizedQuantity;
		}
		if (normalizedSupplyPrice == 0 && normalizedTotalAmount > 0) {
			normalizedSupplyPrice = Math.round(normalizedTotalAmount / 1.1f);
		}
		if (normalizedProductCost == 0 && normalizedSupplyPrice > 0 && normalizedQuantity > 0) {
			normalizedProductCost = Math.round((float) normalizedSupplyPrice / normalizedQuantity);
		}
		if (normalizedQuantity == 0 && normalizedProductCost > 0 && normalizedSupplyPrice > 0) {
			normalizedQuantity = Math.max(1, Math.round((float) normalizedSupplyPrice / normalizedProductCost));
		}
		if (normalizedTotalAmount == 0 && normalizedSupplyPrice > 0) {
			normalizedTotalAmount = Math.round(normalizedSupplyPrice * 1.1f);
		}
		if (normalizedSupplyPrice == 0 && normalizedProductCost > 0 && normalizedQuantity > 0) {
			normalizedSupplyPrice = normalizedProductCost * normalizedQuantity;
			normalizedTotalAmount = Math.round(normalizedSupplyPrice * 1.1f);
		}

		return new MoneySnapshot(nonNegative(normalizedProductCost, "단가"), nonNegative(normalizedQuantity, "수량"),
				nonNegative(normalizedSupplyPrice, "공급가"), nonNegative(normalizedTotalAmount, "총비용"));
	}

	private int nonNegative(int value, String label) {
		if (value < 0) {
			throw new IllegalArgumentException(label + "는 0보다 작을 수 없습니다.");
		}
		return value;
	}

	private OrderStatus parseOrderStatus(String statusStr) {
		if (statusStr == null || statusStr.isBlank()) {
			throw new IllegalArgumentException("발주상태 값이 비어 있습니다.");
		}

		try {
			return OrderStatus.valueOf(statusStr);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("올바르지 않은 발주상태입니다. status=" + statusStr, e);
		}
	}

	private String firstNotBlank(String submittedValue, String currentValue) {
		String normalizedSubmittedValue = normalizeNullableText(submittedValue);
		if (normalizedSubmittedValue != null) {
			return normalizedSubmittedValue;
		}

		return normalizeNullableText(currentValue);
	}

	private void updateRequesterIfNeeded(Order order, Optional<Long> companyId, Optional<Long> requesterMemberId) {
		Optional<Long> normalizedCompanyId = normalizeId(companyId);
		Optional<Long> normalizedRequesterMemberId = normalizeId(requesterMemberId);

		Member newRequester = null;

		if (normalizedRequesterMemberId.isPresent()) {
			Long requesterId = normalizedRequesterMemberId.get();

			newRequester = memberRepository.findById(requesterId)
					.orElseThrow(() -> new IllegalArgumentException("Invalid requesterMemberId. id=" + requesterId));

			if (normalizedCompanyId.isPresent()) {
				Long selectedCompanyId = normalizedCompanyId.get();

				Company selectedCompany = companyRepository.findById(selectedCompanyId)
						.orElseThrow(() -> new IllegalArgumentException("Invalid companyId. id=" + selectedCompanyId));

				if (newRequester.getCompany() == null
						|| !Objects.equals(newRequester.getCompany().getId(), selectedCompany.getId())) {
					throw new IllegalStateException("선택한 멤버가 해당 대리점에 소속되어 있지 않습니다.");
				}
			}

		} else if (normalizedCompanyId.isPresent()) {
			Long selectedCompanyId = normalizedCompanyId.get();

			Company selectedCompany = companyRepository.findById(selectedCompanyId)
					.orElseThrow(() -> new IllegalArgumentException("Invalid companyId. id=" + selectedCompanyId));

			newRequester = findRepresentativeMember(selectedCompany).orElseThrow(() -> new IllegalStateException(
					"선택한 대리점에 대표회원이 없어 신청자를 자동 지정할 수 없습니다. 대리점 상세에서 대표회원(CUSTOMER_REPRESENTATIVE)을 먼저 등록해 주세요."));
		}

		if (newRequester == null) {
			return;
		}

		Task task = order.getTask();
		if (task == null) {
			throw new IllegalStateException("Order에 Task가 존재하지 않습니다. orderId=" + order.getId());
		}

		task.setRequestedBy(newRequester);
		task.setUpdatedAt(LocalDateTime.now());
		taskRepository.save(task);
	}

	private Optional<Member> findRepresentativeMember(Company company) {
		if (company == null || company.getId() == null) {
			return Optional.empty();
		}

		return memberRepository.findByCompany_Id(company.getId()).stream()
				.filter(member -> member != null && member.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE)
				.min((left, right) -> Long.compare(left.getId() == null ? Long.MAX_VALUE : left.getId(),
						right.getId() == null ? Long.MAX_VALUE : right.getId()));
	}

	private void updateOrderItemOptionJson(Order order, String submittedOptionJson) {
		OrderItem orderItem = order.getOrderItem();

		if (orderItem == null) {
			if (submittedOptionJson == null || submittedOptionJson.isBlank()) {
				return;
			}
			throw new IllegalStateException("OrderItem이 없어 옵션을 수정할 수 없습니다. orderId=" + order.getId());
		}

		LinkedHashMap<String, String> originalMap = parseOptionJson(orderItem.getOptionJson());
		LinkedHashMap<String, String> submittedMap = parseOptionJson(submittedOptionJson);

		if (submittedMap.isEmpty() && (submittedOptionJson == null || submittedOptionJson.isBlank())) {
			return;
		}

		LinkedHashMap<String, String> mergedMap = new LinkedHashMap<>();

		for (Map.Entry<String, String> originalEntry : originalMap.entrySet()) {
			String key = originalEntry.getKey();
			String originalValue = originalEntry.getValue();

			if (OPTION_DELETE_BLOCKED_KEYS.contains(key)) {
				String value = OPTION_VALUE_FIXED_KEYS.contains(key) ? originalValue
						: submittedMap.getOrDefault(key, originalValue);

				if (value == null || value.isBlank()) {
					throw new IllegalArgumentException(key + " 값은 비울 수 없습니다.");
				}

				mergedMap.put(key, value.trim());
				continue;
			}

			if (submittedMap.containsKey(key)) {
				String value = submittedMap.get(key);
				if (value == null || value.isBlank()) {
					throw new IllegalArgumentException(key + " 값은 비울 수 없습니다.");
				}
				mergedMap.put(key, value.trim());
			}
		}

		for (Map.Entry<String, String> submittedEntry : submittedMap.entrySet()) {
			String key = submittedEntry.getKey();
			String value = submittedEntry.getValue();

			if (key == null || key.isBlank() || mergedMap.containsKey(key)) {
				continue;
			}
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException(key + " 값은 비울 수 없습니다.");
			}
			mergedMap.put(key.trim(), value.trim());
		}

		LinkedHashMap<String, String> normalizedMap = normalizeOptionSequence(mergedMap);

		try {
			orderItem.setOptionJson(objectMapper.writeValueAsString(normalizedMap));

			String color = normalizedMap.get("색상");
			if (color != null && !color.isBlank()) {
				orderItem.setProductionColor(color.trim());
			}

			String size = normalizedMap.get("사이즈");
			if (size != null && !size.isBlank()) {
				orderItem.setProductionSize(size.trim());
			}

		} catch (Exception e) {
			throw new IllegalArgumentException("옵션 JSON 저장 중 오류가 발생했습니다.", e);
		}
	}

	private LinkedHashMap<String, String> parseOptionJson(String optionJson) {
		if (optionJson == null || optionJson.isBlank()) {
			return new LinkedHashMap<>();
		}

		try {
			LinkedHashMap<String, String> parsed = objectMapper.readValue(optionJson,
					new TypeReference<LinkedHashMap<String, String>>() {
					});
			return parsed != null ? parsed : new LinkedHashMap<>();
		} catch (Exception e) {
			throw new IllegalArgumentException("옵션 JSON을 파싱할 수 없습니다.", e);
		}
	}

	private LinkedHashMap<String, String> normalizeOptionSequence(LinkedHashMap<String, String> sourceMap) {
		LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
		List<String> optionValues = new ArrayList<>();
		boolean hasBaseOptionKey = sourceMap.containsKey("옵션");

		for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			if (isOptionSequenceKey(key)) {
				optionValues.add(value);
			} else {
				normalized.put(key, value);
			}
		}

		for (int i = 0; i < optionValues.size(); i++) {
			String key = hasBaseOptionKey ? (i == 0 ? "옵션" : "옵션" + (i + 1)) : "옵션" + (i + 1);
			normalized.put(key, optionValues.get(i));
		}

		return normalized;
	}

	private boolean isOptionSequenceKey(String key) {
		if (key == null) {
			return false;
		}
		return key.matches("^옵션\\d*$");
	}

	private void deleteAdminImages(Order order, List<Long> deleteAdminImageIds) {
		if (order == null || deleteAdminImageIds == null || deleteAdminImageIds.isEmpty()) {
			return;
		}

		Long orderId = order.getId();

		for (Long imageId : deleteAdminImageIds) {
			if (imageId == null) {
				continue;
			}

			OrderImage image = orderImageRepository
					.findByIdAndOrder_IdAndTypeIgnoreCase(imageId, orderId, ADMIN_IMAGE_TYPE).orElse(null);

			if (image == null) {
				continue;
			}

			deletePhysicalFile(image);

			if (order.getOrderImages() != null) {
				order.getOrderImages().removeIf(
						orderImage -> orderImage != null && Objects.equals(orderImage.getId(), image.getId()));
			}

			orderImageRepository.delete(image);
		}
	}

	private void deletePhysicalFile(OrderImage image) {
		Path filePath = resolveImageFilePath(image);
		if (filePath == null) {
			return;
		}
		try {
			Files.deleteIfExists(filePath);
		} catch (IOException e) {
			throw new RuntimeException("관리자 이미지 파일 삭제 중 오류가 발생했습니다. path=" + filePath, e);
		}
	}

	private Path resolveImageFilePath(OrderImage image) {
		if (image == null) {
			return null;
		}

		if (image.getPath() != null && !image.getPath().isBlank()) {
			return Paths.get(image.getPath()).normalize();
		}

		String url = image.getUrl();
		if (url == null || url.isBlank()) {
			return null;
		}

		String prefix = "/upload/";
		if (!url.startsWith(prefix)) {
			return null;
		}

		String relativePath = url.substring(prefix.length());
		if (relativePath.isBlank()) {
			return null;
		}

		return Paths.get(uploadRootPath, relativePath).normalize();
	}

	private void saveAdminImages(Order order, List<MultipartFile> files) {
		if (order == null || files == null || files.isEmpty()) {
			return;
		}

		List<MultipartFile> validFiles = files.stream().filter(file -> file != null && !file.isEmpty()).toList();

		if (validFiles.isEmpty()) {
			return;
		}

		Long memberId = resolveRequesterMemberId(order);
		LocalDate today = LocalDate.now();
		String dateFolder = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		Path saveDir = Paths.get(uploadRootPath, "order", "order", String.valueOf(memberId), dateFolder, "admin");

		try {
			Files.createDirectories(saveDir);
			List<OrderImage> imageEntities = new ArrayList<>();

			for (MultipartFile file : validFiles) {
				String originalFilename = file.getOriginalFilename();
				String ext = getFileExtension(originalFilename);
				String uuidFileName = UUID.randomUUID() + (ext != null ? "." + ext : "");
				Path savedPath = saveDir.resolve(uuidFileName).normalize();

				Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

				String urlPath = "/upload/order/order/" + memberId + "/" + dateFolder + "/admin/" + uuidFileName;

				OrderImage image = new OrderImage();
				image.setOrder(order);
				image.setFilename(originalFilename);
				image.setUrl(urlPath);
				image.setType(ADMIN_IMAGE_TYPE);
				image.setPath(savedPath.toString());
				image.setUploadedAt(LocalDateTime.now());

				imageEntities.add(image);
			}

			if (!imageEntities.isEmpty()) {
				orderImageRepository.saveAll(imageEntities);

				if (order.getOrderImages() != null) {
					order.getOrderImages().addAll(imageEntities);
				}
			}

		} catch (IOException e) {
			throw new RuntimeException("관리자 이미지 저장 중 오류가 발생했습니다.", e);
		}
	}

	private Long resolveRequesterMemberId(Order order) {
		if (order.getTask() == null) {
			throw new IllegalStateException("Order에 Task가 존재하지 않아 이미지 저장 경로를 만들 수 없습니다.");
		}
		if (order.getTask().getRequestedBy() == null) {
			throw new IllegalStateException("Task에 requestedBy가 없어 이미지 저장 경로를 만들 수 없습니다.");
		}
		return order.getTask().getRequestedBy().getId();
	}

	private String getFileExtension(String filename) {
		if (filename == null || filename.isBlank() || !filename.contains(".")) {
			return null;
		}
		String ext = filename.substring(filename.lastIndexOf('.') + 1).trim();
		if (ext.isBlank()) {
			return null;
		}
		return ext.toLowerCase();
	}

	private record MoneySnapshot(int productCost, int quantity, int supplyPrice, int totalAmount) {
	}
}
