package com.dev.HiddenBATHAuto.controller.page;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.ApiResponse;
import com.dev.HiddenBATHAuto.dto.MemberSaveDTO;
import com.dev.HiddenBATHAuto.dto.client.CompanyListRowDto;
import com.dev.HiddenBATHAuto.dto.employee.EmployeeUpdateResult;
import com.dev.HiddenBATHAuto.dto.employeeDetail.ConflictDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.EmployeeUpdateRequest;
import com.dev.HiddenBATHAuto.dto.employeeDetail.MemberRegionSimpleDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionBulkSaveRequest;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.model.auth.Province;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.MemberAdminService;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.auth.CompanyService;
import com.dev.HiddenBATHAuto.service.auth.MemberManagementService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.order.OrderStatusService;
import com.dev.HiddenBATHAuto.service.order.OrderUpdateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/management")
@RequiredArgsConstructor
public class ManagementController {

	private final TaskRepository taskRepository;
	private final OrderRepository orderRepository;
	private final MemberRepository memberRepository;
	private final DeliveryMethodRepository deliveryMethodRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final OrderUpdateService orderUpdateService;
	private final AsTaskService asTaskService;
	private final OrderStatusService orderStatusService;
	private final TeamRepository teamRepository;
	private final ProvinceRepository provinceRepository;
	private final MemberService memberService;
	private final CompanyService companyService;
	private final CompanyRepository companyRepository;
	private final ObjectMapper objectMapper;
	private final OrderImageRepository orderImageRepository;
	private final MemberManagementService memberMgmtService;
	// ✅ 추가 서비스
	private final MemberAdminService memberAdminService;

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	@GetMapping("/nonStandardTaskList")
    public String nonStandardTaskList(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "all") String dateCriteria,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "all") String productCategoryId,
            @RequestParam(required = false, defaultValue = "REQUESTED") String orderStatus,
            @RequestParam(required = false, defaultValue = "all") String standard,

            // ✅ 정렬 파라미터 (기본값 정리)
            @RequestParam(required = false, defaultValue = "orderDate") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,

            @PageableDefault(size = 10) Pageable pageable,
            Model model
    ) {
        // 1) dateCriteria 정규화 (변경없음)
        String finalDateCriteria = normalizeDateCriteria(dateCriteria);

        // 2) 날짜 범위 (변경없음)
        DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);

        // 3) standard 파싱 (변경없음)
        Boolean standardBool = parseStandardOrNull(standard);

        // 4) category/status 파싱 (변경없음)
        Long categoryId = parseLongOrNullAllowAll(productCategoryId);
        OrderStatus statusEnum = parseOrderStatusOrNullWithDefault(orderStatus, OrderStatus.REQUESTED);

        // 5) keyword 정리 (변경없음)
        String finalKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // ✅ 6) 정렬 매핑
        String sortProperty = mapSortFieldToProperty(sortField);

        // ✅ 6-1) sortDir 안정화 (공백/줄바꿈 방지)
        String safeSortDir = (sortDir == null) ? "desc" : sortDir.trim();
        Sort.Direction direction = "asc".equalsIgnoreCase(safeSortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        // ✅ 7) Pageable에 정렬 적용
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(direction, sortProperty)
        );

        // ✅ 8) Repository: JPQL에 ORDER BY가 없어야 pageable sort가 먹습니다.
        Page<Order> orders = orderRepository.findFilteredOrders(
                finalKeyword,
                finalDateCriteria,
                range.getStart(),
                range.getEnd(),
                categoryId,
                statusEnum,
                standardBool,
                sortedPageable
        );

        // ✅ 페이지네이션 계산 (변경없음)
        int currentPage1 = orders.getPageable().getPageNumber() + 1;
        int startPageNum = Math.max(1, currentPage1 - 4);
        int endPageNum = Math.min(orders.getTotalPages(), currentPage1 + 4);

        model.addAttribute("orders", orders);
        model.addAttribute("startPage", startPageNum);
        model.addAttribute("endPage", endPageNum);

        // (변경없음) 필터 데이터
        model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));
        model.addAttribute("orderStatuses", OrderStatus.values());

        // (변경없음) 필터 유지
        model.addAttribute("keyword", (keyword == null) ? "" : keyword);
        model.addAttribute("dateCriteria", finalDateCriteria);

        model.addAttribute("startDate", range.getStartDateStr());
        model.addAttribute("endDate", range.getEndDateStr());
        model.addAttribute("startDateStr", range.getStartDateStr());
        model.addAttribute("endDateStr", range.getEndDateStr());

        model.addAttribute("productCategoryId", (productCategoryId == null) ? "all" : productCategoryId);
        model.addAttribute("orderStatus", (orderStatus == null) ? OrderStatus.REQUESTED.name() : orderStatus);
        model.addAttribute("standard", (standard == null) ? "all" : standard);

        // ✅ 정렬 유지 (safe 값으로 내려줌)
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", "asc".equalsIgnoreCase(safeSortDir) ? "asc" : "desc");

        // ✅ 페이지 사이즈 유지
        model.addAttribute("pageSize", orders.getSize());

        return "administration/management/order/nonStandard/taskList";
    }

    // ✅ UI에서 선택한 필드명을 실제 JPA 정렬 property로 변환
    private String mapSortFieldToProperty(String sortField) {
        Map<String, String> mapping = Map.of(
                "agencyName", "task.requestedBy.company.companyName",
                "requesterName", "task.requestedBy.name",
                "standard", "standard",
                "orderDate", "createdAt",
                "preferredDeliveryDate", "preferredDeliveryDate",
                "status", "status"
        );
        return mapping.getOrDefault(sortField, "createdAt");
    }

	@GetMapping("/nonStandardOrder/excel")
	public void downloadNonStandardOrderExcel(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String dateCriteria, @RequestParam(required = false) String startDate, // ✅
																													// String
																													// (빈값
																													// 안전)
			@RequestParam(required = false) String endDate, // ✅ String (빈값 안전)
			@RequestParam(required = false) String orderStatus,
			@RequestParam(required = false) String productCategoryId, @RequestParam(required = false) String standard,
			HttpServletResponse response) throws IOException {

		String finalDateCriteria = normalizeDateCriteria(dateCriteria);
		DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);

		Long categoryId = parseLongOrNullAllowAll(productCategoryId);
		OrderStatus status = parseOrderStatusOrNullWithDefault(orderStatus, null); // excel은 기본 강제 안함(넘어온 값 기준)
		// └ 기존 코드도 null/all이면 전체였으니 동작 유지
		Boolean isStandard = parseStandardOrNull(standard);

		String finalKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

		List<Order> orderList = orderRepository.findFilteredOrdersForExcel(finalKeyword, finalDateCriteria,
				range.getStart(), range.getEnd(), categoryId, status, isStandard);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=non_standard_orders.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("비규격발주");

			// 스타일
			CellStyle headerStyle = workbook.createCellStyle();
			Font boldFont = workbook.createFont();
			boldFont.setBold(true);
			headerStyle.setFont(boldFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);

			CellStyle borderedStyle = workbook.createCellStyle();
			borderedStyle.setBorderTop(BorderStyle.THIN);
			borderedStyle.setBorderBottom(BorderStyle.THIN);
			borderedStyle.setBorderLeft(BorderStyle.THIN);
			borderedStyle.setBorderRight(BorderStyle.THIN);

			CellStyle wrapStyle = workbook.createCellStyle();
			wrapStyle.cloneStyleFrom(borderedStyle);
			wrapStyle.setWrapText(true);

			// 헤더
			String[] headers = { "대리점명", "신청자", "신청일", "배송희망일", "우편번호", "도", "시", "구", "도로명주소", "상세주소", "수량", "제품비용",
					"주문메모", "팀카테고리", "배송수단", "배송담당자", "옵션 정보" };

			Row header = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, 5000);
			}

			int rowIdx = 1;

			for (Order order : orderList) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				OrderItem item = order.getOrderItem();

				String agencyName = safe(() -> order.getTask().getRequestedBy().getCompany().getCompanyName(), "미지정");
				String requester = safe(() -> order.getTask().getRequestedBy().getName(), "미지정");
				String createdAt = (order.getCreatedAt() != null) ? order.getCreatedAt().toString() : "";
				String deliveryDate = (order.getPreferredDeliveryDate() != null)
						? order.getPreferredDeliveryDate().toString()
						: "";

				String zip = defaultIfNull(order.getZipCode());
				String doName = defaultIfNull(order.getDoName());
				String siName = defaultIfNull(order.getSiName());
				String guName = defaultIfNull(order.getGuName());
				String road = defaultIfNull(order.getRoadAddress());
				String detail = defaultIfNull(order.getDetailAddress());

				int quantity = order.getQuantity();
				int productCost = order.getProductCost();
				String comment = defaultIfNull(order.getOrderComment());

				String category = safe(() -> order.getProductCategory().getName(), "미지정");
				String deliveryMethod = safe(() -> order.getDeliveryMethod().getMethodName(), "미지정");
				String handler = safe(() -> order.getAssignedDeliveryHandler().getName(), "미지정");

				row.createCell(0).setCellValue(agencyName);
				row.createCell(1).setCellValue(requester);
				row.createCell(2).setCellValue(createdAt);
				row.createCell(3).setCellValue(deliveryDate);

				row.createCell(4).setCellValue(zip);
				row.createCell(5).setCellValue(doName);
				row.createCell(6).setCellValue(siName);
				row.createCell(7).setCellValue(guName);
				row.createCell(8).setCellValue(road);
				row.createCell(9).setCellValue(detail);

				row.createCell(10).setCellValue(quantity);
				row.createCell(11).setCellValue(productCost);
				row.createCell(12).setCellValue(comment);
				row.createCell(13).setCellValue(category);
				row.createCell(14).setCellValue(deliveryMethod);
				row.createCell(15).setCellValue(handler);

				// ✅ 옵션: 줄바꿈 없이 " / "로만 + 끝 찌꺼기 없음
				String optionsText = buildOptionsTextNoTrailing(item);

				Cell optionCell = row.createCell(16);
				optionCell.setCellValue(optionsText);
				optionCell.setCellStyle(wrapStyle);

				// 기본 테두리 적용(기존 borderedStyle 의도 유지)
				for (int i = 0; i <= 15; i++) {
					Cell c = row.getCell(i);
					if (c != null)
						c.setCellStyle(borderedStyle);
				}
			}

			workbook.write(response.getOutputStream());
		}
	}

	// 1) dateCriteria 정규화 (all/order/delivery만 허용)
	private String normalizeDateCriteria(String dateCriteria) {
		if (dateCriteria == null || dateCriteria.isBlank())
			return "all";
		String v = dateCriteria.trim().toLowerCase();
		if ("order".equals(v) || "delivery".equals(v))
			return v;
		return "all";
	}

	// 2) dateCriteria=all이면 날짜필터 미적용(null/null) 처리
	private DateRange buildDateRangeForCriteria(String dateCriteria, String startDateStr, String endDateStr) {
		String dc = normalizeDateCriteria(dateCriteria);
		if ("all".equals(dc)) {
			// 날짜 입력값은 뷰 유지용으로만 넘기고, 조회는 null/null로 (날짜필터 미적용)
			String s = (startDateStr == null) ? "" : startDateStr.trim();
			String e = (endDateStr == null) ? "" : endDateStr.trim();
			return new DateRange(null, null, s, e);
		}
		// 기존 buildDateRange 재사용 (yyyy-MM-dd)
		return buildDateRange(startDateStr, endDateStr);
	}

	// 3) productCategoryId: all/빈값/오류 -> null
	private Long parseLongOrNullAllowAll(String v) {
		if (v == null)
			return null;
		String s = v.trim();
		if (s.isEmpty() || "all".equalsIgnoreCase(s))
			return null;
		try {
			return Long.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// 4) orderStatus: all/빈값 -> null, 잘못된 값 -> defaultValue (defaultValue도 null 가능)
	private OrderStatus parseOrderStatusOrNullWithDefault(String v, OrderStatus defaultValue) {
		if (v == null)
			return defaultValue;
		String s = v.trim();
		if (s.isEmpty() || "all".equalsIgnoreCase(s))
			return null;
		try {
			return OrderStatus.valueOf(s);
		} catch (IllegalArgumentException e) {
			return defaultValue;
		}
	}

	// 5) standard: all/빈값/오류 -> null
	private Boolean parseStandardOrNull(String standard) {
		if (standard == null)
			return null;
		String s = standard.trim().toLowerCase();
		if (s.isEmpty() || "all".equals(s))
			return null;
		if ("true".equals(s))
			return Boolean.TRUE;
		if ("false".equals(s))
			return Boolean.FALSE;
		return null;
	}

	// 6) 옵션 텍스트: " / "로 join (끝 찌꺼기/줄바꿈 없음)
	private String buildOptionsTextNoTrailing(OrderItem item) {
		if (item == null)
			return "";

		Map<String, String> parsedOptionMap = item.getParsedOptionMap();

		if ((parsedOptionMap == null || parsedOptionMap.isEmpty()) && item.getOptionJson() != null
				&& !item.getOptionJson().isBlank()) {
			try {
				parsedOptionMap = objectMapper.readValue(item.getOptionJson(),
						new TypeReference<Map<String, String>>() {
						});
			} catch (Exception e) {
				return "오류: 옵션 파싱 실패";
			}
		}

		if (parsedOptionMap == null || parsedOptionMap.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (Map.Entry<String, String> entry : parsedOptionMap.entrySet()) {
			if (i++ > 0)
				sb.append(" / ");
			sb.append(entry.getKey()).append(": ").append(entry.getValue());
		}
		return sb.toString();
	}

	// Null-safe getter
	private <T> String safe(Supplier<T> getter, String defaultValue) {
		try {
			T value = getter.get();
			return value != null ? value.toString() : defaultValue;
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private String defaultIfNull(String value) {
		return (value != null) ? value : "";
	}

	@GetMapping("/nonStandardTaskDetail/{id}")
	public String nonStandardTaskDetail(@PathVariable Long id, Model model) {
		Task task = taskRepository.findById(id).orElseThrow();

		ObjectMapper objectMapper = new ObjectMapper();

		for (Order order : task.getOrders()) {
			// 1. OrderItem optionJson → parsedOptionMap
			OrderItem item = order.getOrderItem();
			if (item != null) {
				try {
					Map<String, String> parsed = objectMapper.readValue(item.getOptionJson(),
							new com.fasterxml.jackson.core.type.TypeReference<>() {
							});
					item.setParsedOptionMap(parsed);
				} catch (Exception e) {
					System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
				}
			}

			// 2. OrderImage 파일 사이즈 계산
			List<OrderImage> images = order.getOrderImages();
			if (images != null) {
				for (OrderImage image : images) {
					if (image.getPath() != null) {
						File file = new File(image.getPath());
						if (file.exists() && file.isFile()) {
							image.setFileSizeKb(file.length() / 1024); // KB 단위 저장
						} else {
							image.setFileSizeKb(0L); // 없으면 0 처리
						}
					} else {
						image.setFileSizeKb(0L);
					}
				}
			}
		}

		model.addAttribute("task", task);
		return "administration/management/order/nonStandard/taskDetail";
	}

	@GetMapping("/nonStandardOrderItemDetail/{orderId}")
	public String nonStandardOrderItemDetail(@PathVariable Long orderId, Model model) {
		Order order = orderRepository.findById(orderId).orElseThrow();

		// 옵션 파싱
		if (order.getOrderItem() != null) {
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				Map<String, String> parsed = objectMapper.readValue(order.getOrderItem().getOptionJson(),
						new TypeReference<Map<String, String>>() {
						});
				model.addAttribute("optionMap", parsed);
			} catch (Exception e) {
				System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
				model.addAttribute("optionMap", Map.of());
			}
		}

		// ✅ 추가 데이터 (기존)
		model.addAttribute("order", order);
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("deliveryTeamMembers", memberRepository.findByTeamName("배송팀"));
		model.addAttribute("productionTeamMembers", memberRepository.findByTeamName("생산팀"));
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));

		// ✅ 대리점/신청자 드롭다운용 데이터
		// 현재 신청자
		Member currentRequester = order.getTask() != null ? order.getTask().getRequestedBy() : null;
		Long selectedMemberId = (currentRequester != null) ? currentRequester.getId() : null;
		Long selectedCompanyId = (currentRequester != null && currentRequester.getCompany() != null)
				? currentRequester.getCompany().getId()
				: null;

		// 모든 회사 목록
		List<Company> companies = companyRepository.findAll();

		// 현재 선택된 회사의 멤버 목록(초기 렌더용)
		List<Member> companyMembers = (selectedCompanyId != null) ? memberRepository.findByCompany_Id(selectedCompanyId)
				: List.of();

		model.addAttribute("companies", companies);
		model.addAttribute("companyMembers", companyMembers);
		model.addAttribute("selectedCompanyId", selectedCompanyId);
		model.addAttribute("selectedMemberId", selectedMemberId);

		return "administration/management/order/nonStandard/orderItemDetail";
	}

	@PostMapping("/nonStandardOrderItemUpdate/{orderId}")
	public String updateNonStandardOrderItem(@PathVariable Long orderId, @RequestParam("productCost") int productCost,
			@RequestParam("preferredDeliveryDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDeliveryDate,
			@RequestParam("status") String statusStr, @RequestParam("deliveryMethodId") Optional<Long> deliveryMethodId,
			@RequestParam("assignedDeliveryHandlerId") Optional<Long> deliveryHandlerId,
			@RequestParam("productCategoryId") Optional<Long> productCategoryId,
			@RequestParam(value = "companyId", required = false) Optional<Long> companyId,
			@RequestParam(value = "requesterMemberId", required = false) Optional<Long> requesterMemberId,
			@RequestParam(value = "adminImages", required = false) List<MultipartFile> adminImages) {
		orderUpdateService.updateOrder(orderId, productCost, preferredDeliveryDate, statusStr, deliveryMethodId,
				deliveryHandlerId, productCategoryId, companyId, requesterMemberId, adminImages);

		return "redirect:/management/nonStandardOrderItemDetail/" + orderId;
	}

	@DeleteMapping("/order-image/delete/{id}")
	@ResponseBody
	public ResponseEntity<Void> deleteOrderImage(@PathVariable Long id) {
		OrderImage image = orderImageRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 이미지가 존재하지 않습니다."));
		try {
			Files.deleteIfExists(Paths.get(image.getPath())); // ✅ 실제 파일 삭제
		} catch (IOException e) {
			throw new RuntimeException("파일 삭제 실패", e);
		}
		orderImageRepository.delete(image); // ✅ DB 삭제
		return ResponseEntity.ok().build();
	}

	@GetMapping("/asList")
	public String asList(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) Long handlerId, @RequestParam(required = false) AsStatus status,
			@RequestParam(required = false) String dateType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			Pageable pageable, Model model) {
		// 1) dateType 기본값 보정
		String resolvedDateType = (dateType == null || dateType.isBlank()) ? "requested" : dateType;

		// 2) 날짜 범위 변환 (toDate는 포함 검색을 위해 +1day start로 < end 처리)
		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		// 3) 정렬이 비어있으면 dateType 기준으로 기본 정렬 부여
		Pageable resolvedPageable = resolvePageable(pageable, resolvedDateType);

		// 4) 조회
		Page<AsTask> asPage = asTaskService.getFilteredAsListPage(handlerId, status, resolvedDateType, start, end,
				resolvedPageable);

		// 5) 모델
		model.addAttribute("asPage", asPage);
		model.addAttribute("asHandlers", memberRepository.findByTeamName("AS팀"));
		model.addAttribute("selectedHandlerId", handlerId);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedDateType", resolvedDateType);
		model.addAttribute("selectedFromDate", fromDate);
		model.addAttribute("selectedToDate", toDate);

		return "administration/management/as/asList";
	}

	@GetMapping("/asDetail/{id}")
	public String asDetail(@PathVariable Long id, Model model) {
		AsTask asTask = asTaskService.getAsDetail(id);

		model.addAttribute("asTask", asTask);
		model.addAttribute("asStatuses", AsStatus.values());
		model.addAttribute("asTeamMembers", memberRepository.findByTeamName("AS팀"));

		return "administration/management/as/asDetail";
	}

	@GetMapping("/asList/excel")
	public void downloadAsListExcel(@RequestParam(required = false) Long handlerId,
			@RequestParam(required = false) AsStatus status, @RequestParam(required = false) String dateType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			HttpServletResponse response) throws IOException {

		// 1) dateType 기본값 보정
		String resolvedDateType = (dateType == null || dateType.isBlank()) ? "requested" : dateType;

		// 2) 날짜 범위 변환
		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		// 3) ✅ 엑셀은 페이지네이션 없이 "검색 결과 전체" 조회
		List<AsTask> asTasks = asTaskService.getFilteredAsListAll(handlerId, status, resolvedDateType, start, end);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=as_task_list.xlsx");

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("AS 목록");

			// 스타일
			CellStyle headerStyle = workbook.createCellStyle();
			Font boldFont = workbook.createFont();
			boldFont.setBold(true);
			headerStyle.setFont(boldFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);

			CellStyle borderedStyle = workbook.createCellStyle();
			borderedStyle.setBorderTop(BorderStyle.THIN);
			borderedStyle.setBorderBottom(BorderStyle.THIN);
			borderedStyle.setBorderLeft(BorderStyle.THIN);
			borderedStyle.setBorderRight(BorderStyle.THIN);

			CellStyle wrapStyle = workbook.createCellStyle();
			wrapStyle.cloneStyleFrom(borderedStyle);
			wrapStyle.setWrapText(true);

			// 헤더
			Row header = sheet.createRow(0);
			String[] titles = { "대리점명", "요청자", "제목", "요청일", "상태", "배정팀", "담당자", "주소", "요청사유", "금액", "비고" };
			for (int i = 0; i < titles.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(titles[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, (i == 7 || i == 8 || i == 10) ? 10000 : 5000);
			}

			// 데이터
			int rowIdx = 1;
			for (AsTask task : asTasks) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				String companyName = "-";
				if (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null) {
					companyName = safe(task.getRequestedBy().getCompany().getCompanyName());
				}

				String requesterName = (task.getRequestedBy() != null) ? safe(task.getRequestedBy().getName()) : "-";
				String subject = safe(task.getSubject());

				String requestedAt = (task.getRequestedAt() != null) ? task.getRequestedAt().format(dtf) : "";
				String st = (task.getStatus() != null) ? task.getStatus().name() : "";

				String assignedTeam = (task.getAssignedTeam() != null) ? safe(task.getAssignedTeam().getName()) : "";
				String handlerName = (task.getAssignedHandler() != null) ? safe(task.getAssignedHandler().getName())
						: "";

				String address = (safe(task.getRoadAddress()) + " " + safe(task.getDetailAddress())).trim();
				String reason = safe(task.getReason());
				String price = String.valueOf(task.getPrice());
				String comment = safe(task.getAsComment());

				createCell(row, 0, companyName, borderedStyle);
				createCell(row, 1, requesterName, borderedStyle);
				createCell(row, 2, subject, borderedStyle);
				createCell(row, 3, requestedAt, borderedStyle);
				createCell(row, 4, st, borderedStyle);
				createCell(row, 5, assignedTeam, borderedStyle);
				createCell(row, 6, handlerName, borderedStyle);
				createCell(row, 7, address, wrapStyle);
				createCell(row, 8, reason, wrapStyle);
				createCell(row, 9, price, borderedStyle);
				createCell(row, 10, comment, wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
	}

	private Pageable resolvePageable(Pageable pageable, String dateType) {
		if (pageable == null) {
			Sort sort = "processed".equals(dateType) ? Sort.by(Sort.Direction.DESC, "asProcessDate")
					: Sort.by(Sort.Direction.DESC, "requestedAt");
			return PageRequest.of(0, 20, sort);
		}

		// 정렬이 명시된 경우 그대로 사용
		if (pageable.getSort() != null && pageable.getSort().isSorted()) {
			return pageable;
		}

		Sort sort = "processed".equals(dateType) ? Sort.by(Sort.Direction.DESC, "asProcessDate")
				: Sort.by(Sort.Direction.DESC, "requestedAt");

		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

	private void createCell(Row row, int col, String value, CellStyle style) {
		Cell c = row.createCell(col);
		c.setCellValue(value != null ? value : "");
		c.setCellStyle(style);
	}

	private String safe(String v) {
		return (v == null) ? "" : v;
	}

	@PostMapping("/asUpdate/{id}")
	public String updateAsTask(@PathVariable Long id, @RequestParam(required = false) Integer price,
			@RequestParam String status, @RequestParam(required = false) Long assignedHandlerId) {

		asTaskService.updateAsTask(id, price, status, assignedHandlerId);

		return "redirect:/management/asDetail/" + id;
	}

	// =========================
	// 1) 리스트 페이지
	// =========================
	@GetMapping("/productionList")
	public String productionListPage(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String startDate, // ✅
																												// String으로
																												// 변경(빈값
																												// 안전)
			@RequestParam(required = false) String endDate, // ✅ String으로 변경(빈값 안전)
			@RequestParam(required = false) String dateType, Pageable pageable, Model model) {
		// 1) 카테고리
		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

		// 2) dateType 정규화 (null/이상값 방지)
		String finalDateType = normalizeDateType(dateType);

		// 3) 날짜 파싱 (빈값/오류 안전)
		LocalDate startD = parseLocalDateOrDefault(startDate, LocalDate.now());
		LocalDate endD = parseLocalDateOrDefault(endDate, LocalDate.now());

		// end < start 방지 (사용자 입력 실수 방어)
		if (endD.isBefore(startD)) {
			LocalDate tmp = startD;
			startD = endD;
			endD = tmp;
		}

		LocalDateTime start = startD.atStartOfDay();
		LocalDateTime end = endD.atTime(LocalTime.MAX);

		// 4) status 정규화
		StatusNormalizeResult statusResult = normalizeStatus(status, OrderStatus.CONFIRMED);
		OrderStatus parsedStatus = statusResult.parsedStatus;
		String finalStatusParam = statusResult.statusParam; // 화면/링크에 유지할 status 문자열

		// 5) 조회 (AND 조건은 서비스에서 처리: 날짜범위 + 팀카테고리 + 상태 + dateType)
		Page<Order> orders = orderStatusService.getOrders(start, end, category, parsedStatus, finalDateType, pageable);

		// 6) View Model
		model.addAttribute("orders", orders);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("status", finalStatusParam);

		// ✅ 템플릿은 String으로 받게 변경
		model.addAttribute("startDateStr", startD.format(DateTimeFormatter.ISO_DATE));
		model.addAttribute("endDateStr", endD.format(DateTimeFormatter.ISO_DATE));

		model.addAttribute("dateType", finalDateType);

		// 팀 카테고리 목록
		model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/production/productionList";
	}

	// =========================
	// 2) 엑셀 다운로드
	// =========================
	@GetMapping("/productionList/excel")
	public void downloadProductionListExcel(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String startDate, // ✅
																												// String으로
																												// 변경(빈값
																												// 안전)
			@RequestParam(required = false) String endDate, // ✅ String으로 변경(빈값 안전)
			@RequestParam(required = false) String dateType, HttpServletResponse response) throws IOException {

		// 1) 카테고리
		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

		// 2) dateType 정규화 (리스트와 동일)
		String finalDateType = normalizeDateType(dateType);

		// 3) 날짜 파싱 (리스트와 동일)
		LocalDate startD = parseLocalDateOrDefault(startDate, LocalDate.now());
		LocalDate endD = parseLocalDateOrDefault(endDate, LocalDate.now());

		if (endD.isBefore(startD)) {
			LocalDate tmp = startD;
			startD = endD;
			endD = tmp;
		}

		LocalDateTime start = startD.atStartOfDay();
		LocalDateTime end = endD.atTime(LocalTime.MAX);

		// 4) status 정규화 (리스트와 동일)
		StatusNormalizeResult statusResult = normalizeStatus(status, OrderStatus.CONFIRMED);
		OrderStatus parsedStatus = statusResult.parsedStatus;

		// 5) 전체 조회 (리스트와 동일 조건)
		List<Order> orders = orderStatusService.getAllOrders(start, end, category, parsedStatus, finalDateType);

		// 6) 응답 헤더
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=production_task_orders.xlsx");

		// 7) 엑셀 생성
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Production Orders");

			// ===== 스타일 =====
			DataFormat df = workbook.createDataFormat();

			CellStyle headerStyle = workbook.createCellStyle();
			Font boldFont = workbook.createFont();
			boldFont.setBold(true);
			headerStyle.setFont(boldFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			setThinBorder(headerStyle);
			headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			CellStyle borderedStyle = workbook.createCellStyle();
			setThinBorder(borderedStyle);
			borderedStyle.setVerticalAlignment(VerticalAlignment.TOP);

			CellStyle wrapStyle = workbook.createCellStyle();
			wrapStyle.cloneStyleFrom(borderedStyle);
			wrapStyle.setWrapText(true);

			CellStyle moneyStyle = workbook.createCellStyle();
			moneyStyle.cloneStyleFrom(borderedStyle);
			moneyStyle.setDataFormat(df.getFormat("#,##0"));

			// 라벨행(대리점/요청자/발주일)은 헤더스타일 그대로 쓰되 wrap만 켬
			CellStyle labelStyle = workbook.createCellStyle();
			labelStyle.cloneStyleFrom(headerStyle);
			labelStyle.setWrapText(true);

			// ===== 컬럼 폭(고정) =====
			// 주소 / 수량 / 가격 / 배송희망일 / 배송수단 / 배송담당자 / 상세사항
			sheet.setColumnWidth(0, 14000);
			sheet.setColumnWidth(1, 4000);
			sheet.setColumnWidth(2, 6000);
			sheet.setColumnWidth(3, 8000);
			sheet.setColumnWidth(4, 7000);
			sheet.setColumnWidth(5, 7000);
			sheet.setColumnWidth(6, 20000);

			int rowIdx = 0;
			Long lastTaskId = null;

			// 보기 편하게 상단 고정(첫 번째 헤더가 만들어지면 freeze)
			boolean freezeApplied = false;

			for (Order order : orders) {
				Task task = order.getTask();

				// Task 구분 블록 시작
				if (task != null && (lastTaskId == null || !task.getId().equals(lastTaskId))) {

					// (1) 라벨 행 (병합)
					Row labelRow = sheet.createRow(rowIdx++);
					labelRow.setHeightInPoints(28);

					Cell labelCell = labelRow.createCell(0);

					String companyName = (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null)
							? task.getRequestedBy().getCompany().getCompanyName()
							: "";

					String requesterName = (task.getRequestedBy() != null) ? task.getRequestedBy().getName() : "";

					String taskCreatedAt = (task.getCreatedAt() != null) ? task.getCreatedAt().toString() : "";

					// ✅ 라벨도 줄바꿈 최소화: " / " 기준
					labelCell.setCellValue(
							"[대리점명] " + companyName + " / [요청자명] " + requesterName + " / [발주일] " + taskCreatedAt);
					labelCell.setCellStyle(labelStyle);

					// 0~6 병합
					sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

					// (2) Order 테이블 헤더
					Row header = sheet.createRow(rowIdx++);
					header.setHeightInPoints(20);

					String[] titles = { "배송지 주소", "수량", "가격", "배송희망일", "배송수단", "배송담당자", "상세사항" };
					for (int i = 0; i < titles.length; i++) {
						Cell cell = header.createCell(i);
						cell.setCellValue(titles[i]);
						cell.setCellStyle(headerStyle);
					}

					// 첫 헤더 기준 상단 고정
					if (!freezeApplied) {
						// 헤더 아래부터 스크롤: 현재 rowIdx 위치가 '데이터 시작행'이므로 그 위까지 고정
						sheet.createFreezePane(0, rowIdx);
						freezeApplied = true;
					}

					lastTaskId = task.getId();
				}

				// (3) Order 내용 행
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(90);

				// 주소 (wrap)
				String addr = safe(order.getRoadAddress()) + " " + safe(order.getDetailAddress());
				Cell c0 = row.createCell(0);
				c0.setCellValue(addr.trim());
				c0.setCellStyle(wrapStyle);

				// 수량
				Cell c1 = row.createCell(1);
				c1.setCellValue(order.getQuantity());
				c1.setCellStyle(borderedStyle);

				// 가격 (money format)
				Cell c2 = row.createCell(2);
				c2.setCellValue(order.getProductCost());
				c2.setCellStyle(moneyStyle);

				// 배송희망일
				Cell c3 = row.createCell(3);
				c3.setCellValue(
						order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : "");
				c3.setCellStyle(borderedStyle);

				// 배송수단
				Cell c4 = row.createCell(4);
				c4.setCellValue(
						order.getDeliveryMethod() != null ? safe(order.getDeliveryMethod().getMethodName()) : "");
				c4.setCellStyle(borderedStyle);

				// 배송담당자
				Cell c5 = row.createCell(5);
				c5.setCellValue(
						order.getAssignedDeliveryHandler() != null ? safe(order.getAssignedDeliveryHandler().getName())
								: "");
				c5.setCellStyle(borderedStyle);

				// 상세사항 (✅ 줄바꿈 없이 " / "로만 구성)
				OrderItem item = order.getOrderItem();
				StringBuilder detail = new StringBuilder();

				if (order.getProductCategory() != null) {
					detail.append("카테고리: ").append(safe(order.getProductCategory().getName()));
				}

				if (item != null) {
					if (detail.length() > 0)
						detail.append(" / ");
					detail.append("제품명: ").append(safe(item.getProductName()));

					if (item.getOptionJson() != null && !item.getOptionJson().isBlank()) {
						try {
							Map<String, String> optionMap = objectMapper.readValue(item.getOptionJson(),
									new TypeReference<Map<String, String>>() {
									});

							for (Map.Entry<String, String> entry : optionMap.entrySet()) {
								detail.append(" / ").append(safe(entry.getKey())).append(": ")
										.append(safe(entry.getValue()));
							}
						} catch (Exception e) {
							detail.append(" / [옵션 파싱 실패]");
						}
					}
				}

				Cell c6 = row.createCell(6);
				c6.setCellValue(detail.toString().trim());
				c6.setCellStyle(wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
	}

	// =========================
	// Helper
	// =========================

	private static class StatusNormalizeResult {
		final OrderStatus parsedStatus; // null이면 "전체"
		final String statusParam; // 화면/링크 유지용(전체면 "")

		StatusNormalizeResult(OrderStatus parsedStatus, String statusParam) {
			this.parsedStatus = parsedStatus;
			this.statusParam = statusParam;
		}
	}

	/**
	 * status 파라미터 정규화 정책: - null: 기본값(defaultStatus) 적용 + statusParam도
	 * defaultStatus.name() - blank(""): 전체(null) + statusParam="" - 그 외: Enum 파싱 성공
	 * 시 해당값, 실패 시 defaultStatus
	 */
	private static StatusNormalizeResult normalizeStatus(String status, OrderStatus defaultStatus) {
		if (status == null) {
			return new StatusNormalizeResult(defaultStatus, defaultStatus.name());
		}
		if (status.isBlank()) {
			return new StatusNormalizeResult(null, "");
		}
		try {
			OrderStatus parsed = OrderStatus.valueOf(status.trim());
			return new StatusNormalizeResult(parsed, parsed.name());
		} catch (IllegalArgumentException e) {
			return new StatusNormalizeResult(defaultStatus, defaultStatus.name());
		}
	}

	private static LocalDate parseLocalDateOrDefault(String v, LocalDate def) {
		if (v == null)
			return def;
		String s = v.trim();
		if (s.isEmpty())
			return def;
		try {
			return LocalDate.parse(s, DateTimeFormatter.ISO_DATE);
		} catch (DateTimeParseException e) {
			return def;
		}
	}

	private static void setThinBorder(CellStyle style) {
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
	}

	@GetMapping("/productionDetail/{id}")
	public String productionDetail(@PathVariable Long id, Model model) {
		Order order = orderRepository.findById(id).orElseThrow();

		// 옵션 파싱
		if (order.getOrderItem() != null) {
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				Map<String, String> parsed = objectMapper.readValue(order.getOrderItem().getOptionJson(),
						new TypeReference<>() {
						});
				model.addAttribute("optionMap", parsed);
			} catch (Exception e) {
				System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
				model.addAttribute("optionMap", Map.of());
			}
		}

		// 이미지 타입 맵
		Map<String, String> imageTypeMap = Map.of("고객 업로드", "CUSTOMER", "관리자 업로드", "MANAGEMENT", "배송 완료", "DELIVERY",
				"배송 증빙", "PROOF");
		model.addAttribute("imageTypeMap", imageTypeMap);

		// ✅ 추가 데이터
		model.addAttribute("order", order);
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("deliveryTeamMembers", memberRepository.findByTeamName("배송팀"));
		model.addAttribute("productionTeamMembers", memberRepository.findByTeamName("생산팀"));
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));
		return "administration/management/production/productionDetail";
	}

	@GetMapping("/deliveryList")
	public String deliveryListPage(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assignedMemberId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String dateType, @RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate, Pageable pageable, Model model) {
		// 1) dateType 정규화
		String finalDateType = normalizeDateType(dateType);

		// 2) status 정규화 (화면 기본값: PRODUCTION_DONE)
		StatusResult statusResult = normalizeStatusForList(status);

		// 3) 날짜 파싱 (빈값/NULL 안전) + 범위 계산
		DateRange range = buildDateRange(startDate, endDate);

		// 4) 조회 (ID 기반 필터)
		Page<Order> orders = orderStatusService.getOrders(range.getStart(), range.getEnd(), categoryId,
				assignedMemberId, statusResult.getParsedStatus(), finalDateType, pageable);

		// 5) 모델 등록
		model.addAttribute("orders", orders);

		model.addAttribute("categoryId", categoryId);
		model.addAttribute("assignedMemberId", assignedMemberId);

		model.addAttribute("status", statusResult.getStatusForView());
		model.addAttribute("dateType", finalDateType);

		// ✅ 템플릿에서 #temporals.format 쓰지 말고 그대로 넣도록 문자열 유지
		model.addAttribute("startDate", range.getStartDateStr());
		model.addAttribute("endDate", range.getEndDateStr());

		model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("assignees", memberRepository.findByTeamName("배송팀"));
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/delivery/deliveryList";
	}

	/**
	 * 배송 목록 엑셀 다운로드 (관리자) - 화면과 동일한 필터 규칙으로 조회 - 페이지네이션 없이 "전체 조회 결과" 출력
	 */
	@GetMapping("/deliveryList/excel")
	public void downloadDeliveryListExcel(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String dateType,
			@RequestParam(required = false) Long assignedMemberId, @RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate, HttpServletResponse response) throws IOException {

		String finalDateType = normalizeDateType(dateType);

		// ✅ 엑셀도 화면과 동일 규칙
		StatusResult statusResult = normalizeStatusForExcel(status);

		// ✅ 엑셀도 화면과 동일 규칙(둘 다 없으면 null/null)
		DateRange range = buildDateRange(startDate, endDate);

		// ✅ 엑셀은 전체 조회
		List<Order> orders = orderStatusService.getAllOrders(range.getStart(), range.getEnd(), categoryId,
				assignedMemberId, statusResult.getParsedStatus(), finalDateType);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=delivery_list.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("배송 리스트");

			Row header = sheet.createRow(0);
			String[] headers = { "대리점명", "신청자", "주소", "수량", "제품가격", "배송일", "상태", "담당자", "제품 상세" };
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}

			int rowIdx = 1;
			for (Order order : orders) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(80);

				String companyName = safe(() -> order.getTask().getRequestedBy().getCompany().getCompanyName());
				String requesterName = safe(() -> order.getTask().getRequestedBy().getName());
				String address = (nvl(order.getRoadAddress()) + " " + nvl(order.getDetailAddress())).trim();

				row.createCell(0).setCellValue(companyName);
				row.createCell(1).setCellValue(requesterName);
				row.createCell(2).setCellValue(address);

				// ✅ 문제 지점 수정(원시타입/래퍼타입 모두 안전)
				// - int/long이면 null 비교 자체가 불가하므로, null 체크를 없애고 안전 변환으로 처리
				row.createCell(3).setCellValue(toLongValue(order.getQuantity())); // 수량
				row.createCell(4).setCellValue(toLongValue(order.getProductCost())); // 제품가격

				row.createCell(5).setCellValue(
						order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : "");
				row.createCell(6).setCellValue(order.getStatus() != null ? order.getStatus().name() : "");

				Member deliveryHandler = order.getAssignedDeliveryHandler();
				row.createCell(7).setCellValue(deliveryHandler != null ? nvl(deliveryHandler.getName()) : "");

				// ✅ 옵션 JSON: 줄바꿈 없이 " / "로 연결
				row.createCell(8).setCellValue(buildDetailTextNoNewline(order));
			}

			workbook.write(response.getOutputStream());
		}
	}

	// =========================
	// Helpers (컨트롤러 내부 전용)
	// =========================

	private String normalizeDateType(String dateType) {
		if (dateType == null || dateType.isBlank())
			return "preferred";
		if (!"preferred".equals(dateType) && !"created".equals(dateType))
			return "preferred";
		return dateType;
	}

	/**
	 * 화면 목록: status 파라미터가 null이면 기본값(PRODUCTION_DONE) 강제
	 */
	private StatusResult normalizeStatusForList(String status) {
		if (status == null) {
			return new StatusResult(OrderStatus.PRODUCTION_DONE, OrderStatus.PRODUCTION_DONE.name());
		}
		if (status.isBlank()) {
			return new StatusResult(null, ""); // 전체
		}
		try {
			OrderStatus parsed = OrderStatus.valueOf(status);
			return new StatusResult(parsed, status);
		} catch (IllegalArgumentException e) {
			return new StatusResult(OrderStatus.PRODUCTION_DONE, OrderStatus.PRODUCTION_DONE.name());
		}
	}

	/**
	 * 엑셀: 화면과 동일 규칙
	 */
	private StatusResult normalizeStatusForExcel(String status) {
		return normalizeStatusForList(status);
	}

	/**
	 * startDate/endDate 문자열을 LocalDateTime 범위로 변환 - 둘 다 비어있으면 start/end = null/null
	 * (날짜 필터 미적용)
	 */
	private DateRange buildDateRange(String startDateStr, String endDateStr) {
		String s = (startDateStr == null) ? "" : startDateStr.trim();
		String e = (endDateStr == null) ? "" : endDateStr.trim();

		LocalDateTime start = null;
		LocalDateTime end = null;

		LocalDate sd = parseYmdOrNull(s);
		LocalDate ed = parseYmdOrNull(e);

		if (sd != null)
			start = sd.atStartOfDay();
		if (ed != null)
			end = ed.atTime(LocalTime.MAX);

		return new DateRange(start, end, s, e);
	}

	private LocalDate parseYmdOrNull(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			return LocalDate.parse(s, YMD);
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * 옵션 상세 문자열(줄바꿈 없이 " / ")
	 */
	private String buildDetailTextNoNewline(Order order) {
		StringBuilder detail = new StringBuilder();

		TeamCategory cat = order.getProductCategory();
		if (cat != null) {
			detail.append("카테고리: ").append(nvl(cat.getName()));
		}

		OrderItem item = order.getOrderItem();
		if (item != null) {
			if (detail.length() > 0)
				detail.append(" / ");
			detail.append("제품명: ").append(nvl(item.getProductName()));

			String optionJson = item.getOptionJson();
			if (optionJson != null && !optionJson.isBlank()) {
				try {
					Map<String, String> optionMap = objectMapper.readValue(optionJson,
							new TypeReference<Map<String, String>>() {
							});
					for (Map.Entry<String, String> entry : optionMap.entrySet()) {
						detail.append(" / ").append(entry.getKey()).append(": ").append(entry.getValue());
					}
				} catch (Exception e) {
					detail.append(" / ").append("[옵션 파싱 실패]");
				}
			}
		}

		return detail.toString().trim();
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}

	private String safe(SupplierThrows<String> s) {
		try {
			String v = s.get();
			return v == null ? "" : v;
		} catch (Exception e) {
			return "";
		}
	}

	private long toLongValue(int n) {
		return (long) n;
	}

	@FunctionalInterface
	private interface SupplierThrows<T> {
		T get() throws Exception;
	}

	private static class StatusResult {
		private final OrderStatus parsedStatus;
		private final String statusForView;

		StatusResult(OrderStatus parsedStatus, String statusForView) {
			this.parsedStatus = parsedStatus;
			this.statusForView = statusForView;
		}

		public OrderStatus getParsedStatus() {
			return parsedStatus;
		}

		public String getStatusForView() {
			return statusForView;
		}
	}

	private static class DateRange {
		private final LocalDateTime start;
		private final LocalDateTime end;
		private final String startDateStr;
		private final String endDateStr;

		DateRange(LocalDateTime start, LocalDateTime end, String startDateStr, String endDateStr) {
			this.start = start;
			this.end = end;
			this.startDateStr = startDateStr;
			this.endDateStr = endDateStr;
		}

		public LocalDateTime getStart() {
			return start;
		}

		public LocalDateTime getEnd() {
			return end;
		}

		public String getStartDateStr() {
			return startDateStr;
		}

		public String getEndDateStr() {
			return endDateStr;
		}
	}

	@GetMapping("/deliveryDetail/{id}")
	public String deliveryDetail(@PathVariable Long id, Model model) {
		Order order = orderRepository.findById(id).orElseThrow();

		// 옵션 파싱
		if (order.getOrderItem() != null) {
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				Map<String, String> parsed = objectMapper.readValue(order.getOrderItem().getOptionJson(),
						new TypeReference<>() {
						});
				model.addAttribute("optionMap", parsed);
			} catch (Exception e) {
				System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
				model.addAttribute("optionMap", Map.of());
			}
		}

		// 이미지 타입 맵
		Map<String, String> imageTypeMap = Map.of("고객 업로드", "CUSTOMER", "관리자 업로드", "MANAGEMENT", "배송 완료", "DELIVERY",
				"배송 증빙", "PROOF");
		model.addAttribute("imageTypeMap", imageTypeMap);

		// ✅ 추가 데이터
		model.addAttribute("order", order);
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("deliveryTeamMembers", memberRepository.findByTeamName("배송팀"));
		model.addAttribute("productionTeamMembers", memberRepository.findByTeamName("생산팀"));
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));
		return "administration/management/delivery/deliveryDetail";
	}

	@GetMapping("/clientList")
	public String clientList(@RequestParam(required = false) String keyword,
			@RequestParam(required = false, defaultValue = "company") String searchType,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "10") int size,
			@RequestParam(required = false, defaultValue = "createdAt") String sortField,
			@RequestParam(required = false, defaultValue = "desc") String sortDir, Model model) {
		// ✅ size 안전장치 (원하시면 더 엄격히 제한 가능)
		if (size != 10 && size != 30 && size != 50 && size != 100) {
			size = 10;
		}

		// ✅ sortDir 안전장치
		Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

		// ✅ 여기서는 pageable의 sort는 "기본값" 용도로만 두고,
		// 실제 정렬은 repository custom 구현에서 sortField/sortDir로 처리합니다.
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));

		Page<CompanyListRowDto> companies = companyService.getCompanyList(keyword, searchType, sortField, sortDir,
				pageable);

		model.addAttribute("companies", companies);
		model.addAttribute("keyword", keyword);
		model.addAttribute("searchType", searchType);
		model.addAttribute("size", size);
		model.addAttribute("sortField", sortField);
		model.addAttribute("sortDir", sortDir);

		return "administration/member/client/clientList";
	}

	@GetMapping("/clientList/excel")
    public ResponseEntity<ByteArrayResource> downloadClientListExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "company") String searchType,
            @RequestParam(required = false, defaultValue = "createdAt") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Integer size, // 화면 상태 유지용(필수는 아님)
            @RequestParam(name = "companyIds", required = false) List<Long> companyIds
    ) throws IOException {

        if (companyIds == null || companyIds.isEmpty()) {
            // 체크박스 미선택 방어
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(new ByteArrayResource("선택된 대리점이 없습니다.".getBytes()));
        }

        byte[] bytes = companyService.exportCompaniesToExcelByIds(companyIds, sortField, sortDir);

        String filename = "company_list.xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }

	@GetMapping("/clientDetail/{id}")
	public String clientDetail(@PathVariable Long id, Model model) {
		Company company = companyRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 대리점이 존재하지 않습니다. ID=" + id));

		List<Member> memberList = memberRepository.findByCompany(companyRepository.findById(id).get());

		model.addAttribute("company", company);
		model.addAttribute("members", memberList);

		return "administration/member/client/clientDetail";
	}

	@PostMapping("/clientUpdate")
	@ResponseBody
	public String clientUpdate() {
		return "success";
	}

	// =========================================================
	// ✅ 추가 API 1) 멤버 비밀번호 초기화 + SMS 발송
	// =========================================================
	@PostMapping("/member/{memberId}/resetPassword")
	@ResponseBody
	public ResponseEntity<?> resetPassword(@PathVariable Long memberId) {
		memberAdminService.resetPasswordAndSendSms(memberId);
		return ResponseEntity.ok(Map.of("result", "success"));
	}

	// =========================================================
	// ✅ 추가 API 2) 멤버 접속금지(enabled=false)
	// =========================================================
	@PostMapping("/member/{memberId}/disable")
	@ResponseBody
	public ResponseEntity<?> disableMember(@PathVariable Long memberId) {
		memberAdminService.disableMember(memberId);
		return ResponseEntity.ok(Map.of("result", "success"));
	}

	@GetMapping("/employeeList")
	public String employeeList(@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "teamId", required = false) Long teamId,
			@RequestParam(value = "sortField", required = false, defaultValue = "createdAt") String sortField,
			@RequestParam(value = "sortDir", required = false, defaultValue = "desc") String sortDir,
			@PageableDefault(size = 10) Pageable pageable, Model model) {

		// 1) 팀 목록(이름 오름차순)
		List<Team> teams = teamRepository.findAllOrderedByName();

		// 2) 정렬 생성(팀 정렬 시 팀카테고리 묶음 처리)
		Sort sort = buildEmployeeSort(sortField, sortDir);

		// 3) Pageable에 sort 반영
		Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

		// 4) 직원 검색(우리회사 직원만 + 직원 role만) - 기존 기능 유지
		Page<Member> employeePage = memberService.searchEmployees(name, teamId, sortedPageable);

		// 5) 페이지네이션(5개 윈도우)
		int totalPages = employeePage.getTotalPages();
		int current = employeePage.getNumber(); // 0-based
		int window = 5;

		int pageStart = 0;
		int pageEnd = 0;
		if (totalPages > 0) {
			pageStart = Math.max(0, current - (window / 2));
			pageEnd = Math.min(totalPages - 1, pageStart + window - 1);
			pageStart = Math.max(0, pageEnd - window + 1);
		}

		// 6) 모델 바인딩
		model.addAttribute("teams", teams);
		model.addAttribute("employeePage", employeePage);
		model.addAttribute("name", name);
		model.addAttribute("teamId", teamId);

		model.addAttribute("sortField", sortField);
		model.addAttribute("sortDir", sortDir);

		model.addAttribute("pageStart", pageStart);
		model.addAttribute("pageEnd", pageEnd);

		return "administration/member/employee/employeeList";
	}

	/**
	 * ✅ EXCEL 다운로드 (체크된 항목만 / 현재 페이지 기준) - ids: "1,2,3" 형태로 전달됨
	 */
	@PostMapping("/employeeList/excel-selected")
	public void employeeListExcelSelected(@RequestParam(value = "ids", required = false) String ids,
			HttpServletResponse response) throws IOException {

		// 1) 방어: ids 없음
		if (ids == null || ids.trim().isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		// 2) ids 파싱 + 순서 유지
		List<Long> idList = memberService.parseIdListKeepOrder(ids);
		if (idList.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		// 3) 체크된 직원만 조회 (N+1 방지 EntityGraph) + 요청 순서대로 재정렬
		List<Member> employees = memberService.findEmployeesForExcelByIdsOrdered(idList);

		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet("직원리스트");

			// ===== 스타일 =====
			// 헤더(12pt, bold)
			Font headerFont = wb.createFont();
			headerFont.setFontHeightInPoints((short) 12);
			headerFont.setBold(true);

			CellStyle headerStyle = wb.createCellStyle();
			headerStyle.setFont(headerFont);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);
			headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);

			// 바디(10pt, wrap)
			Font bodyFont = wb.createFont();
			bodyFont.setFontHeightInPoints((short) 10);

			CellStyle bodyStyle = wb.createCellStyle();
			bodyStyle.setFont(bodyFont);
			bodyStyle.setAlignment(HorizontalAlignment.LEFT);
			bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
			bodyStyle.setWrapText(true);
			bodyStyle.setBorderTop(BorderStyle.THIN);
			bodyStyle.setBorderBottom(BorderStyle.THIN);
			bodyStyle.setBorderLeft(BorderStyle.THIN);
			bodyStyle.setBorderRight(BorderStyle.THIN);

			CellStyle bodyCenterStyle = wb.createCellStyle();
			bodyCenterStyle.cloneStyleFrom(bodyStyle);
			bodyCenterStyle.setAlignment(HorizontalAlignment.CENTER);

			// ===== 헤더 =====
			String[] headers = new String[] { "Username", "이름", "전화", "이메일", "롤", "팀", "팀카테고리", "담당구역" };

			Row headerRow = sheet.createRow(0);
			headerRow.setHeightInPoints(22);

			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
			}

			// ===== 데이터 =====
			int rowIdx = 1;

			for (Member m : employees) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(18);

				// 담당구역 텍스트 (기존에 쓰시던 로직 그대로 사용)
				String regionText = memberService.buildRegionText(m);

				setCell(row, 0, safe(m.getUsername()), bodyStyle);
				setCell(row, 1, safe(m.getName()), bodyStyle);
				setCell(row, 2, safe(m.getPhone()), bodyStyle);
				setCell(row, 3, safe(m.getEmail()), bodyStyle);
				setCell(row, 4, (m.getRole() != null ? m.getRole().name() : ""), bodyCenterStyle);

				setCell(row, 5, (m.getTeam() != null ? safe(m.getTeam().getName()) : ""), bodyStyle);
				setCell(row, 6, (m.getTeamCategory() != null ? safe(m.getTeamCategory().getName()) : ""), bodyStyle);
				setCell(row, 7, safe(regionText), bodyStyle);
			}

			// ===== 컬럼 너비 =====
			sheet.setColumnWidth(0, 18 * 256); // Username
			sheet.setColumnWidth(1, 14 * 256); // 이름
			sheet.setColumnWidth(2, 16 * 256); // 전화
			sheet.setColumnWidth(3, 24 * 256); // 이메일
			sheet.setColumnWidth(4, 14 * 256); // 롤
			sheet.setColumnWidth(5, 14 * 256); // 팀
			sheet.setColumnWidth(6, 18 * 256); // 팀카테고리
			sheet.setColumnWidth(7, 40 * 256); // 담당구역(줄바꿈)

			// ===== 응답 헤더 =====
			String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
			String fileName = "직원리스트_" + now + ".xlsx";
			String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);

			try (ServletOutputStream os = response.getOutputStream()) {
				wb.write(os);
				os.flush();
			}
		}
	}

	private void setCell(Row row, int col, String value, CellStyle style) {
		Cell cell = row.createCell(col);
		cell.setCellValue(value == null ? "" : value);
		cell.setCellStyle(style);
	}

	// ===== 정렬 빌더 =====
	private Sort buildEmployeeSort(String sortField, String sortDir) {
		Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

		// 허용 필드만
		if (!"name".equals(sortField) && !"team".equals(sortField) && !"createdAt".equals(sortField)) {
			sortField = "createdAt";
		}

		if ("name".equals(sortField)) {
			// 이름 정렬: 이름 -> id
			return Sort.by(dir, "name").and(Sort.by(Sort.Direction.ASC, "id"));
		}

		if ("team".equals(sortField)) {
			// ✅ 팀 정렬: 팀 -> 팀카테고리(묶음) -> 이름 -> id
			// (요청하신 “팀카테고리 같은 것끼리 모여서”는 이 정렬로 보장됩니다.)
			return Sort.by(dir, "team.name").and(Sort.by(Sort.Direction.ASC, "teamCategory.name"))
					.and(Sort.by(Sort.Direction.ASC, "name")).and(Sort.by(Sort.Direction.ASC, "id"));
		}

		// createdAt 정렬: 등록일 -> id
		return Sort.by(dir, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
	}

	@GetMapping("/employeeDetail/{id}")
	public String employeeDetail(@PathVariable Long id, Model model) {
		Member member = memberRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 직원이 존재하지 않습니다."));

		if (!(member.getRole() == MemberRole.INTERNAL_EMPLOYEE || member.getRole() == MemberRole.MANAGEMENT)) {
			throw new IllegalArgumentException("직원만 조회 가능합니다.");
		}

		model.addAttribute("member", member);
		return "administration/member/employee/employeeDetail";
	}

	@GetMapping("/company/{companyId}/business-license")
	public ResponseEntity<Resource> viewBusinessLicense(@PathVariable Long companyId) throws IOException {
		Company company = companyRepository.findById(companyId)
				.orElseThrow(() -> new IllegalArgumentException("회사 정보를 찾을 수 없습니다. id=" + companyId));

		String pathStr = company.getBusinessLicensePath();
		if (!StringUtils.hasText(pathStr)) {
			return ResponseEntity.notFound().build();
		}

		Path filePath = Paths.get(pathStr);
		if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
			return ResponseEntity.notFound().build();
		}

		Resource resource = toResource(filePath);

		// content-type 추정 (image/png, application/pdf 등)
		String contentType = Files.probeContentType(filePath);
		MediaType mediaType = (contentType != null) ? MediaType.parseMediaType(contentType)
				: MediaType.APPLICATION_OCTET_STREAM;

		// 브라우저에서 "열람"되도록 inline
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(mediaType);
		headers.setContentDisposition(ContentDisposition.inline().build());

		return ResponseEntity.ok().headers(headers).body(resource);
	}

	private Resource toResource(Path filePath) {
		try {
			return new UrlResource(filePath.toUri());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("파일 경로가 올바르지 않습니다: " + filePath, e);
		}
	}

	// ===== 직원 정보 업데이트 =====
	@PostMapping("/employeeUpdate")
	@ResponseBody
	public ResponseEntity<ApiResponse<EmployeeUpdateResult>> employeeUpdate(@RequestBody EmployeeUpdateRequest req) {
		EmployeeUpdateResult result = memberMgmtService.updateEmployee(req);
		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	// ✅ 팀 변경 시 담당구역 전체 삭제(확인 후 호출)
	@DeleteMapping("/member/{memberId}/regions")
	@ResponseBody
	public ResponseEntity<ApiResponse<Void>> deleteAllMemberRegions(@PathVariable Long memberId) {
		memberMgmtService.clearMemberRegions(memberId);
		return ResponseEntity.ok(ApiResponse.ok(null));
	}

	@GetMapping("/employeeInsertForm")
	public String employeeInsertForm(Model model) {
		// 팀 목록
		List<Team> teams = teamRepository.findAll();

		// 팀 카테고리 목록 (모두 불러와서 JS에서 필터링할 수 있게)
		List<TeamCategory> teamCategories = teamCategoryRepository.findByTeamName("생산팀");

		// 시도 정보 (도)
		List<Province> provinces = provinceRepository.findAll();

		model.addAttribute("teams", teams);
		model.addAttribute("teamCategories", teamCategories);
		model.addAttribute("provinces", provinces);

		return "administration/member/employee/employeeInsertForm";
	}

	@PostMapping("/employeeInsert")
	public String employeeInsert(@ModelAttribute MemberSaveDTO request) {
		System.out.println("📥 regionJson 수신: " + request.getRegionJson());

		memberService.saveMember(request);
		return "redirect:/management/employeeInsertForm";
	}

	// ===== 선택지 조회 =====
	@GetMapping("/teams")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<Team>>> teams() {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getTeams()));
	}

	@GetMapping("/teamCategories")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<TeamCategory>>> teamCategories(@RequestParam Long teamId) {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getTeamCategories(teamId)));
	}

	@GetMapping("/memberRoles")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<String>>> memberRoles() {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getMemberRoles()));
	}

	// ===== 행정구역 조회 =====
	@GetMapping("/regions/provinces")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<Province>>> provinces() {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getProvinces()));
	}

	@GetMapping("/regions/cities")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<City>>> cities(@RequestParam Long provinceId) {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getCities(provinceId)));
	}

	@GetMapping("/regions/districts")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<District>>> districts(@RequestParam Long provinceId,
			@RequestParam(required = false) Long cityId) {
		return ResponseEntity.ok(ApiResponse.ok(memberMgmtService.getDistricts(provinceId, cityId)));
	}

	// ManagementController.java (일부)
	@GetMapping(value = "/member/{memberId}/regions", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<MemberRegionSimpleDTO>>> memberRegions(@PathVariable Long memberId) {
		List<MemberRegionSimpleDTO> list = memberMgmtService.getMemberRegionsSimple(memberId);
		return ResponseEntity.ok(ApiResponse.ok(list));
	}

	@DeleteMapping("/member/{memberId}/regions/{memberRegionId}")
	@ResponseBody
	public ResponseEntity<ApiResponse<Void>> deleteMemberRegion(@PathVariable Long memberId,
			@PathVariable Long memberRegionId) {
		memberMgmtService.deleteMemberRegion(memberId, memberRegionId);
		return ResponseEntity.ok(ApiResponse.ok(null));
	}

	// ===== 멤버 담당구역 일괄 저장(충돌 검증) =====
	@PostMapping("/member/regions/bulk")
	@ResponseBody
	public ResponseEntity<ApiResponse<List<ConflictDTO>>> saveMemberRegions(@RequestBody RegionBulkSaveRequest req) {
		List<ConflictDTO> conflicts = memberMgmtService.saveMemberRegions(req);
		if (!conflicts.isEmpty()) {
			return ResponseEntity.ok(ApiResponse.fail("중복된 담당 구역이 있어 저장되지 않았습니다.", conflicts));
		}
		return ResponseEntity.ok(ApiResponse.ok(null));
	}
}
