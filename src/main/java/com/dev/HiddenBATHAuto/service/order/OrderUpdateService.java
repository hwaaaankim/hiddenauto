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
	private static final String DIRECT_DELIVERY_METHOD_NAME = "직배송";
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

	@Value("${spring.upload.path}")
	private String uploadRootPath;

	/**
	 * 기존 호출부 보호용 오버로드입니다. 새 화면에서 추가된 필드는 현재 DB 값을 유지한 채 기존 수정 항목만 반영합니다.
	 */
	@Transactional
	public void updateOrder(Long orderId, int productCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			Optional<Long> companyId, Optional<Long> requesterMemberId, List<Long> deleteAdminImageIds,
			List<MultipartFile> adminImages, String adminMemo) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));

		updateOrder(orderId, productCost, order.getQuantity(), order.getSupplyPrice(), order.getTotalAmount(),
				order.getPackingCost(), order.getDeliveryCost(), preferredDeliveryDate, statusStr, deliveryMethodId,
				deliveryHandlerId, productCategoryId, companyId, requesterMemberId, order.getZipCode(),
				order.getDoName(), order.getSiName(), order.getGuName(), order.getRoadAddress(),
				order.getDetailAddress(), order.getOrdererName(), order.getOrdererPhone(),
				order.getOrderItem() != null ? order.getOrderItem().getOptionJson() : null, deleteAdminImageIds,
				adminImages, adminMemo);
	}

	@Transactional
	public void updateOrder(
	        Long orderId,
	        int productCost,
	        int quantity,
	        int supplyPrice,
	        int totalAmount,
	        int packingCost,
	        int deliveryCost,
	        LocalDate preferredDeliveryDate,
	        String statusStr,
	        Optional<Long> deliveryMethodId,
	        Optional<Long> deliveryHandlerId,
	        Optional<Long> productCategoryId,
	        Optional<Long> companyId,
	        Optional<Long> requesterMemberId,
	        String zipCode,
	        String doName,
	        String siName,
	        String guName,
	        String roadAddress,
	        String detailAddress,
	        String ordererName,
	        String ordererPhone,
	        String optionJson,
	        List<Long> deleteAdminImageIds,
	        List<MultipartFile> adminImages,
	        String adminMemo
	) {
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));

	    OrderStatus status = parseOrderStatus(statusStr);

	    MoneySnapshot moneySnapshot = normalizeMoney(productCost, quantity, supplyPrice, totalAmount);

	    order.setProductCost(moneySnapshot.productCost());
	    order.setQuantity(moneySnapshot.quantity());
	    order.setSupplyPrice(moneySnapshot.supplyPrice());
	    order.setTotalAmount(moneySnapshot.totalAmount());
	    order.setPackingCost(nonNegative(packingCost, "포장비"));
	    order.setDeliveryCost(nonNegative(deliveryCost, "배송비"));

	    order.setPreferredDeliveryDate(preferredDeliveryDate != null ? preferredDeliveryDate.atStartOfDay() : null);
	    order.setStatus(status);

	    order.setZipCode(normalizeNullableText(zipCode));
	    order.setDoName(normalizeNullableText(doName));
	    order.setSiName(normalizeNullableText(siName));
	    order.setGuName(normalizeNullableText(guName));
	    order.setRoadAddress(normalizeNullableText(roadAddress));
	    order.setDetailAddress(normalizeNullableText(detailAddress));

	    order.setOrdererName(normalizeNullableText(ordererName));
	    order.setOrdererPhone(normalizeNullableText(ordererPhone));

	    String normalizedAdminMemo = normalizeNullableText(adminMemo);
	    order.setAdminMemo(normalizedAdminMemo);

	    /*
	     * 배송수단 먼저 저장합니다.
	     * 이후 배송수단이 직배송인지 판단해서 배송담당자/배송순서 인덱스를 동기화합니다.
	     */
	    normalizeId(deliveryMethodId).ifPresentOrElse(id -> {
	        var method = deliveryMethodRepository.findById(id)
	                .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId. id=" + id));
	        order.setDeliveryMethod(method);
	    }, () -> order.setDeliveryMethod(null));

	    boolean canceled = status == OrderStatus.CANCELED;
	    boolean directDelivery = isDirectDeliveryMethod(order);

	    /*
	     * 배송담당자 규칙
	     *
	     * 1. 취소 상태면 배송담당자 제거
	     * 2. 직배송이 아니면 배송담당자 제거
	     * 3. 직배송이면 배송담당자와 배송희망일 필수
	     */
	    if (canceled || !directDelivery) {
	        order.setAssignedDeliveryHandler(null);
	    } else {
	        if (preferredDeliveryDate == null) {
	            throw new IllegalArgumentException("직배송 선택 시 배송희망일은 필수입니다.");
	        }

	        Long handlerId = normalizeId(deliveryHandlerId)
	                .orElseThrow(() -> new IllegalArgumentException("직배송 선택 시 배송팀 담당자는 필수입니다."));

	        Member member = memberRepository.findById(handlerId)
	                .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId. id=" + handlerId));

	        order.setAssignedDeliveryHandler(member);
	    }

	    normalizeId(productCategoryId).ifPresentOrElse(id -> {
	        var category = teamCategoryRepository.findById(id)
	                .orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId. id=" + id));
	        order.setProductCategory(category);
	    }, () -> order.setProductCategory(null));

	    updateRequesterIfNeeded(
	            order,
	            normalizeId(companyId),
	            normalizeId(requesterMemberId)
	    );

	    updateOrderItemOptionJson(order, optionJson);

	    order.setUpdatedAt(LocalDateTime.now());

	    /*
	     * 중요:
	     * 1. 기존 이미지 삭제 먼저
	     * 2. 새 이미지 저장
	     */
	    deleteAdminImages(order, deleteAdminImageIds);
	    saveAdminImages(order, adminImages);

	    /*
	     * 배송순서 인덱스 동기화
	     *
	     * - 취소: 무조건 삭제
	     * - 직배송 아님: 무조건 삭제
	     * - 직배송: ensureIndex(order)
	     */
	    if (canceled || !directDelivery) {
	        deliveryOrderIndexService.removeIndex(order);
	    } else {
	        deliveryOrderIndexService.ensureIndex(order);
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

	private boolean isDirectDeliveryMethod(Order order) {
	    if (order == null || order.getDeliveryMethod() == null) {
	        return false;
	    }

	    String methodName = normalizeNullableText(order.getDeliveryMethod().getMethodName());

	    return DIRECT_DELIVERY_METHOD_NAME.equals(methodName);
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

	private String normalizeNullableText(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void updateRequesterIfNeeded(Order order, Optional<Long> companyId, Optional<Long> requesterMemberId) {
	    Optional<Long> normalizedCompanyId = normalizeId(companyId);
	    Optional<Long> normalizedRequesterMemberId = normalizeId(requesterMemberId);

	    /*
	     * 1. 신청자를 직접 선택한 경우:
	     *    - 해당 멤버로 requestedBy 변경
	     *    - companyId가 같이 넘어왔으면 선택한 회사 소속인지 검증
	     *
	     * 2. 대리점은 선택했지만 신청자를 선택하지 않은 경우:
	     *    - 해당 대리점의 CUSTOMER_REPRESENTATIVE 대표회원으로 자동 지정
	     *
	     * 3. 대리점도 신청자도 비어 있는 경우:
	     *    - 기존 신청자 유지
	     */

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

	        newRequester = findRepresentativeMember(selectedCompany)
	                .orElseThrow(() -> new IllegalStateException(
	                        "선택한 대리점에 대표회원이 없어 신청자를 자동 지정할 수 없습니다. 대리점 상세에서 대표회원(CUSTOMER_REPRESENTATIVE)을 먼저 등록해 주세요."
	                ));
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

	    return memberRepository.findByCompany_Id(company.getId())
	            .stream()
	            .filter(member -> member != null && member.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE)
	            .min((left, right) -> Long.compare(
	                    left.getId() == null ? Long.MAX_VALUE : left.getId(),
	                    right.getId() == null ? Long.MAX_VALUE : right.getId()
	            ));
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

			String productName = normalizedMap.get("제품명");
			if (productName != null && !productName.isBlank()) {
				orderItem.setProductName(productName.trim());
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
			String key;
			if (hasBaseOptionKey) {
				key = (i == 0) ? "옵션" : "옵션" + (i + 1);
			} else {
				key = "옵션" + (i + 1);
			}
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

			/*
			 * 같은 트랜잭션 안에서 order.getOrderImages()가 이미 초기화된 상태일 수 있으므로 컬렉션에서도 제거해 줍니다.
			 */
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
			/*
			 * 여기서 RuntimeException을 던지면 DB 수정까지 롤백됩니다. 이미지 파일 삭제 실패 때문에 주문 수정 전체를 실패시키고 싶으면
			 * RuntimeException으로 바꾸시면 됩니다.
			 */
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

		/*
		 * 예외 대비: 과거 데이터 중 path가 없고 url만 있는 경우 /upload/ 뒤 경로를 uploadRootPath와 합칩니다. url
		 * 예시: /upload/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename} 실제 경로:
		 * {spring.upload.path}/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
		 */
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

		/*
		 * 저장 경로: {spring.upload.path}/order/order/{memberId}/{yyyy-MM-dd}/admin/
		 */
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

				/*
				 * 웹 접근 경로: /upload/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
				 */
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
