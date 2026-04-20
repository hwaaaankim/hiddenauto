package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddRequest;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddSaveResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCreateRequest;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderOptionEntryRequest;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardCategoryRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSeriesRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductOrderAddCommandService {

	private static final Long PRODUCTION_TEAM_ID = 2L;
	private static final Long DELIVERY_TEAM_ID = 3L;
	private static final Long DEFAULT_PRODUCTION_TEAM_CATEGORY_ID = 1L;
	private static final String MANAGEMENT_UPLOAD_TYPE = "MANAGEMENT";
	// 핵심: fallback 전용 팀 ID
	private static final Long DEFAULT_FALLBACK_TEAM_ID = 1L;

	private final CompanyRepository companyRepository;
	private final MemberRepository memberRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final StandardCategoryRepository standardCategoryRepository;
	private final StandardProductSeriesRepository standardProductSeriesRepository;
	private final TaskRepository taskRepository;
	private final OrderRepository orderRepository;
	private final ObjectMapper objectMapper;

	@Value("${spring.upload.path}")
	private String uploadPath;

	public ProductOrderAddSaveResponse create(ProductOrderAddRequest request,
			MultiValueMap<String, MultipartFile> fileMap) {

		Company company = companyRepository.findById(request.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("선택한 업체를 찾을 수 없습니다."));

		Member requestedBy = memberRepository
				.findCompanyMembersByRole(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE, PageRequest.of(0, 1))
				.stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException("해당 업체에 CUSTOMER_REPRESENTATIVE 멤버가 없습니다."));

		Member deliveryHandler = memberRepository.findByIdAndTeam_Id(request.getDeliveryHandlerId(), DELIVERY_TEAM_ID)
				.orElseThrow(() -> new IllegalArgumentException("배송 담당자는 팀 ID 3 소속 멤버만 선택할 수 있습니다."));

		if (request.getOrders() == null || request.getOrders().isEmpty()) {
			throw new IllegalArgumentException("최소 1개의 주문이 필요합니다.");
		}

		validateCommonDeliveryAddress(request);

		LocalDateTime now = LocalDateTime.now();

		Task task = new Task();
		task.setRequestedBy(requestedBy);
		task.setStatus(TaskStatus.REQUESTED);
		task.setTotalPrice(0);
		task.setCreatedAt(now);
		task.setUpdatedAt(now);
		task = taskRepository.save(task);

		int totalPrice = 0;

		for (int i = 0; i < request.getOrders().size(); i++) {
			ProductOrderCreateRequest orderRequest = request.getOrders().get(i);
			ResolvedOrderMeta resolved = resolveOrderMeta(orderRequest);

			LinkedHashMap<String, String> optionMap = buildOptionMap(orderRequest, resolved);

			Order order = new Order();
			order.setTask(task);
			order.setStandard(Boolean.TRUE.equals(orderRequest.getStandard()));

			// productCategory 만 저장
			order.setProductCategory(resolved.productCategory());

			// assignedProductionTeam 은 비움
			order.setAssignedProductionTeam(null);

			order.setAssignedDeliveryTeam(deliveryHandler.getTeamCategory());
			order.setAssignedDeliveryHandler(deliveryHandler);

			// 공통 배송지: 관리자 주소검색 결과 저장
			applyCommonDeliveryAddress(order, request);

			order.setPreferredDeliveryDate(request.getPreferredDeliveryDate().atStartOfDay());
			order.setProductCost(orderRequest.getProductCost());
			order.setQuantity(orderRequest.getQuantity());
			order.setOrderComment(trimToNull(orderRequest.getOrderComment()));
			order.setStatus(OrderStatus.REQUESTED);
			order.setCreatedAt(now);
			order.setUpdatedAt(now);

			OrderItem orderItem = new OrderItem();
			orderItem.setProductName(resolveProductName(optionMap, resolved));
			orderItem.setQuantity(orderRequest.getQuantity());
			orderItem.setOptionJson(toJson(optionMap));

			order.setOrderItem(orderItem);

			order = orderRepository.save(order);

			saveOrderFiles(fileMap == null ? null : fileMap.get("orderFiles_" + i), task.getId(), order);

			totalPrice += order.getProductCost() * order.getQuantity();
		}

		task.setTotalPrice(totalPrice);
		task.setUpdatedAt(LocalDateTime.now());
		taskRepository.save(task);

		return new ProductOrderAddSaveResponse(true, "발주가 등록되었습니다.", task.getId());
	}

	private void validateCommonDeliveryAddress(ProductOrderAddRequest request) {
	    normalizeRequired(request.getZipCode(), "우편번호");
	    normalizeRequired(request.getDoName(), "도/시");
	    normalizeRequired(request.getRoadAddress(), "도로명 주소");
	}

	private void applyCommonDeliveryAddress(Order order, ProductOrderAddRequest request) {
	    order.setZipCode(normalizeRequired(request.getZipCode(), "우편번호"));
	    order.setDoName(normalizeRequired(request.getDoName(), "도/시"));
	    order.setSiName(trimToNull(request.getSiName()));
	    order.setGuName(trimToNull(request.getGuName()));
	    order.setRoadAddress(normalizeRequired(request.getRoadAddress(), "도로명 주소"));
	    order.setDetailAddress(trimToNull(request.getDetailAddress()));
	}
	
	private ResolvedOrderMeta resolveOrderMeta(ProductOrderCreateRequest request) {
		boolean standard = Boolean.TRUE.equals(request.getStandard());

		if (standard) {
			if (request.getStandardCategoryId() == null) {
				throw new IllegalArgumentException("규격 주문은 대분류를 선택해야 합니다.");
			}
			if (request.getStandardProductSeriesId() == null) {
				throw new IllegalArgumentException("규격 주문은 중분류를 선택해야 합니다.");
			}

			StandardCategory category = standardCategoryRepository.findById(request.getStandardCategoryId())
					.orElseThrow(() -> new IllegalArgumentException("선택한 규격 대분류를 찾을 수 없습니다."));

			StandardProductSeries series = standardProductSeriesRepository
					.findByIdAndCategory_Id(request.getStandardProductSeriesId(), request.getStandardCategoryId())
					.orElseThrow(() -> new IllegalArgumentException("선택한 규격 중분류를 찾을 수 없습니다."));

			// 핵심: 규격은 중분류명이 아니라 대분류명 기준으로 생산팀 카테고리 매칭
			TeamCategory productCategory = resolveProductionCategoryForStandardCategory(category.getName());

			return new ResolvedOrderMeta(productCategory, category.getName(), series.getName(), series.getId());
		}

		if (request.getProductionCategoryId() == null) {
			throw new IllegalArgumentException("비규격 주문은 생산팀 분류를 선택해야 합니다.");
		}

		TeamCategory productCategory = teamCategoryRepository
				.findByIdAndTeam_Id(request.getProductionCategoryId(), PRODUCTION_TEAM_ID)
				.orElseThrow(() -> new IllegalArgumentException("선택한 생산팀 분류를 찾을 수 없습니다."));

		return new ResolvedOrderMeta(productCategory, productCategory.getName(), null, null);
	}

	private TeamCategory resolveProductionCategoryForStandardCategory(String standardCategoryName) {
		if (standardCategoryName == null || standardCategoryName.trim().isBlank()) {
			throw new IllegalArgumentException("규격 대분류명이 비어 있습니다.");
		}

		String normalizedName = standardCategoryName.trim();

		return teamCategoryRepository
				// 1차: 생산팀(팀 ID 2) 안에서 대분류명과 동일한 카테고리 찾기
				.findFirstByTeam_IdAndNameIgnoreCase(PRODUCTION_TEAM_ID, normalizedName)

				// 2차: 없으면 fallback 은 무조건 id=1, team.id=1
				.or(() -> teamCategoryRepository.findByIdAndTeam_Id(DEFAULT_PRODUCTION_TEAM_CATEGORY_ID,
						DEFAULT_FALLBACK_TEAM_ID))

				.orElseThrow(() -> new IllegalArgumentException("규격 대분류명 '" + normalizedName
						+ "' 와 일치하는 생산팀 카테고리가 없고, 기본 TeamCategory(id=1, team.id=1)도 찾을 수 없습니다."));
	}

	private LinkedHashMap<String, String> buildOptionMap(ProductOrderCreateRequest request,
			ResolvedOrderMeta resolved) {
		LinkedHashMap<String, String> optionMap = new LinkedHashMap<>();

		optionMap.put("카테고리", resolved.categoryName());

		if (Boolean.TRUE.equals(request.getStandard())) {
			optionMap.put("제품시리즈", resolved.seriesName());
			optionMap.put("제품시리즈ID", String.valueOf(resolved.seriesId()));
		}

		if (request.getOptionEntries() == null || request.getOptionEntries().isEmpty()) {
			throw new IllegalArgumentException("주문 옵션은 최소 1개 이상 입력해야 합니다.");
		}

		for (ProductOrderOptionEntryRequest entry : request.getOptionEntries()) {
			String title = normalizeRequired(entry.getTitle(), "옵션 제목");
			String answer = normalizeRequired(entry.getAnswer(), "옵션 답변");
			optionMap.put(title, answer);
		}

		return optionMap;
	}

	private String resolveProductName(LinkedHashMap<String, String> optionMap, ResolvedOrderMeta resolved) {
		String productName = optionMap.get("제품명");
		if (productName != null && !productName.isBlank()) {
			return productName.trim();
		}

		if (resolved.seriesName() != null && !resolved.seriesName().isBlank()) {
			return resolved.seriesName();
		}
		return resolved.categoryName();
	}

	private void saveOrderFiles(List<MultipartFile> files, Long taskId, Order order) {
		if (files == null || files.isEmpty()) {
			return;
		}

		String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
		Path uploadDir = Paths.get(normalizeDir(uploadPath), "order", "management", String.valueOf(taskId),
				String.valueOf(order.getId()), dateFolder);

		try {
			Files.createDirectories(uploadDir);

			for (MultipartFile file : files) {
				if (file == null || file.isEmpty()) {
					continue;
				}

				String originalFilename = StringUtils
						.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));

				String extension = getExtension(originalFilename);
				String savedFilename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);

				Path targetPath = uploadDir.resolve(savedFilename);
				file.transferTo(targetPath);

				OrderImage orderImage = new OrderImage();
				orderImage.setType(MANAGEMENT_UPLOAD_TYPE);
				orderImage.setFilename(originalFilename);
				orderImage.setPath(targetPath.toString().replace("\\", "/"));
				orderImage.setUrl("/upload/order/management/" + taskId + "/" + order.getId() + "/" + dateFolder + "/"
						+ savedFilename);
				orderImage.setUploadedAt(LocalDateTime.now());

				order.addOrderImage(orderImage);
			}

			order.setUpdatedAt(LocalDateTime.now());
			orderRepository.save(order);

		} catch (IOException e) {
			throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	private String toJson(LinkedHashMap<String, String> optionMap) {
		try {
			return objectMapper.writeValueAsString(optionMap);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("옵션 JSON 생성에 실패했습니다.");
		}
	}

	private String normalizeRequired(String value, String fieldName) {
		if (value == null || value.trim().isBlank()) {
			throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
		}
		return value.trim();
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isBlank() ? null : trimmed;
	}

	private String normalizeDir(String dir) {
		if (!StringUtils.hasText(dir))
			return dir;

		String d = dir.replace("\\", "/").trim();
		String userHome = System.getProperty("user.home");
		if (StringUtils.hasText(userHome)) {
			userHome = userHome.replace("\\", "/");
			d = d.replace("${user.home}", userHome);

			if (d.equals("~")) {
				d = userHome;
			} else if (d.startsWith("~/")) {
				d = userHome + d.substring(1);
			}
		}

		if (!d.endsWith("/")) {
			d = d + "/";
		}
		return d;
	}

	private String getExtension(String filename) {
		int idx = filename.lastIndexOf('.');
		if (idx < 0 || idx == filename.length() - 1) {
			return "";
		}
		return filename.substring(idx + 1);
	}

	private record ResolvedOrderMeta(TeamCategory productCategory, String categoryName, String seriesName,
			Long seriesId) {
	}
}