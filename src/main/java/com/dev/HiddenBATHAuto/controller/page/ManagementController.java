package com.dev.HiddenBATHAuto.controller.page;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.MemberSaveDTO;
import com.dev.HiddenBATHAuto.model.auth.Company;
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
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.auth.CompanyService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.order.OrderStatusService;
import com.dev.HiddenBATHAuto.service.order.OrderUpdateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	private final ObjectMapper objectMapper; // com.fasterxml.jackson.databind.ObjectMapper
	private final OrderImageRepository orderImageRepository;

	@GetMapping("/standardOrderList")
	public String standardOrderList() {

		return "administration/order/standard/orderList";
	}

	@GetMapping("/standardOrderDetail")
	public String standardOrderDetail() {

		return "administration/order/standard/orderDetail";
	}

	@GetMapping("/nonStandardTaskList")
	public String nonStandardTaskList(@RequestParam(required = false, defaultValue = "") String keyword,
			@RequestParam(required = false, defaultValue = "all") String dateCriteria,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false, defaultValue = "all") String productCategoryId,
			@RequestParam(required = false, defaultValue = "REQUESTED") String orderStatus,
			@RequestParam(required = false, defaultValue = "all") String deliveryMethodId,
			@PageableDefault(size = 10) Pageable pageable, Model model) {
		LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
		LocalDateTime endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

		Page<Order> orders = orderRepository.findFilteredOrders(keyword.isBlank() ? null : keyword, dateCriteria,
				startDateTime, endDateTime, productCategoryId.equals("all") ? null : Long.parseLong(productCategoryId),
				orderStatus.equals("all") ? null : OrderStatus.valueOf(orderStatus),
				deliveryMethodId.equals("all") ? null : Long.parseLong(deliveryMethodId), pageable);

		int startPage = Math.max(1, orders.getPageable().getPageNumber() - 4);
		int endPage = Math.min(orders.getTotalPages(), orders.getPageable().getPageNumber() + 4);

		model.addAttribute("orders", orders);
		model.addAttribute("startPage", startPage);
		model.addAttribute("endPage", endPage);

		// 필터용 데이터
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("orderStatuses", OrderStatus.values());

		// 🔁 필터 유지용 바인딩
		model.addAttribute("keyword", keyword);
		model.addAttribute("dateCriteria", dateCriteria);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("productCategoryId", productCategoryId);
		model.addAttribute("orderStatus", orderStatus);
		model.addAttribute("deliveryMethodId", deliveryMethodId);

		return "administration/management/order/nonStandard/taskList";
	}

	@GetMapping("/nonStandardOrder/excel")
	public void downloadNonStandardOrderExcel(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String dateCriteria,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String orderStatus, @RequestParam(required = false) String deliveryMethodId,
			@RequestParam(required = false) String productCategoryId, HttpServletResponse response) throws IOException {

		// ✅ 날짜 변환
		LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
		LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

		// ✅ 타입 변환
		Long categoryId = (productCategoryId == null || "all".equals(productCategoryId)) ? null
				: Long.valueOf(productCategoryId);
		OrderStatus status = (orderStatus == null || "all".equals(orderStatus)) ? null
				: OrderStatus.valueOf(orderStatus);
		Long deliveryId = (deliveryMethodId == null || "all".equals(deliveryMethodId)) ? null
				: Long.valueOf(deliveryMethodId);

		// ✅ 데이터 조회
		List<Order> orderList = orderRepository.findFilteredOrdersForExcel(keyword, dateCriteria, startDateTime,
				endDateTime, categoryId, status, deliveryId);

		// ✅ 응답 설정
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=non_standard_orders.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("비규격발주");

			// ✅ 스타일
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

			// ✅ 헤더 작성
			String[] headers = { "대리점명", "신청자", "신청일", "배송희망일", "우편번호", "도", "시", "구", "도로명주소", "상세주소", "수량", "제품비용",
					"주문메모", "팀카테고리", "배송수단", "배송담당자", "옵션 정보" };

			Row header = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, 5000);
			}

			ObjectMapper objectMapper = new ObjectMapper();
			int rowIdx = 1;

			// ✅ 데이터 작성
			for (Order order : orderList) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				OrderItem item = order.getOrderItem();

				// null 방어
				String agencyName = safe(() -> order.getTask().getRequestedBy().getCompany().getCompanyName(), "미지정");
				String requester = safe(() -> order.getTask().getRequestedBy().getName(), "미지정");
				String createdAt = order.getCreatedAt() != null ? order.getCreatedAt().toString() : "";
				String deliveryDate = order.getPreferredDeliveryDate() != null
						? order.getPreferredDeliveryDate().toString()
						: "";

				String zip = defaultIfNull(order.getZipCode());
				String doName = defaultIfNull(order.getDoName());
				String siName = defaultIfNull(order.getSiName());
				String guName = defaultIfNull(order.getGuName());
				String road = defaultIfNull(order.getRoadAddress());
				String detail = defaultIfNull(order.getDetailAddress());

				int quantity = order.getQuantity() != 0 ? order.getQuantity() : 0;
				int productCost = order.getProductCost();
				String comment = defaultIfNull(order.getOrderComment());

				String category = safe(() -> order.getProductCategory().getName(), "미지정");
				String deliveryMethod = safe(() -> order.getDeliveryMethod().getMethodName(), "미지정");
				String handler = safe(() -> order.getAssignedDeliveryHandler().getName(), "미지정");

				// 작성
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

				// 옵션 정보
				StringBuilder optionsText = new StringBuilder();
				Map<String, String> parsedOptionMap = (item != null) ? item.getParsedOptionMap() : null;

				if ((parsedOptionMap == null || parsedOptionMap.isEmpty()) && item != null
						&& item.getOptionJson() != null) {
					try {
						parsedOptionMap = objectMapper.readValue(item.getOptionJson(), new TypeReference<>() {
						});
					} catch (Exception e) {
						parsedOptionMap = Map.of("오류", "옵션 파싱 실패");
					}
				}

				if (parsedOptionMap != null) {
					int count = 0;
					for (Map.Entry<String, String> entry : parsedOptionMap.entrySet()) {
						optionsText.append(entry.getKey()).append(": ").append(entry.getValue());
						count++;
						if (count % 3 == 0) {
							optionsText.append("\n");
						} else {
							optionsText.append(" / ");
						}
					}
				}

				Cell optionCell = row.createCell(16);
				optionCell.setCellValue(optionsText.toString());
				optionCell.setCellStyle(wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
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
						new TypeReference<>() {
						});
				model.addAttribute("optionMap", parsed);
			} catch (Exception e) {
				System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
				model.addAttribute("optionMap", Map.of());
			}
		}

		// ✅ 추가 데이터
		model.addAttribute("order", order);
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("deliveryTeamMembers", memberRepository.findByTeamName("배송팀"));
		model.addAttribute("productionTeamMembers", memberRepository.findByTeamName("생산팀"));
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));

		return "administration/management/order/nonStandard/orderItemDetail";
	}

	@PostMapping("/nonStandardOrderItemUpdate/{orderId}")
	public String updateNonStandardOrderItem(@PathVariable Long orderId, @RequestParam("productCost") int productCost,
			@RequestParam("preferredDeliveryDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDeliveryDate,
			@RequestParam("status") String statusStr, @RequestParam("deliveryMethodId") Optional<Long> deliveryMethodId,
			@RequestParam("assignedDeliveryHandlerId") Optional<Long> deliveryHandlerId,
			@RequestParam("productCategoryId") Optional<Long> productCategoryId,
			@RequestParam(value = "adminImages", required = false) List<MultipartFile> adminImages) {

		orderUpdateService.updateOrder(orderId, productCost, preferredDeliveryDate, statusStr, deliveryMethodId,
				deliveryHandlerId, productCategoryId, adminImages);

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

		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		Page<AsTask> asPage = asTaskService.getFilteredAsList(handlerId, status, dateType, start, end, pageable);

		model.addAttribute("asPage", asPage);
		model.addAttribute("asHandlers", memberRepository.findByTeamName("AS팀"));
		model.addAttribute("selectedHandlerId", handlerId);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedDateType", dateType);
		model.addAttribute("selectedFromDate", fromDate);
		model.addAttribute("selectedToDate", toDate);

		return "administration/management/as/asList";
	}

	@GetMapping("/asDetail/{id}")
	public String asDetail(@PathVariable Long id, Model model) {
		AsTask asTask = asTaskService.getAsDetail(id);

		model.addAttribute("asTask", asTask);
		model.addAttribute("asStatuses", AsStatus.values());

		// ✅ 필요 시 서비스 통해 AS팀 멤버 조회
		model.addAttribute("asTeamMembers", memberRepository.findByTeamName("AS팀"));

		return "administration/management/as/asDetail";
	}

	@GetMapping("/asList/excel")
	public void downloadAsListExcel(@RequestParam(required = false) Long handlerId,
			@RequestParam(required = false) AsStatus status, @RequestParam(required = false) String dateType, // "requested"
																												// or
																												// "processed"
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			HttpServletResponse response) throws IOException {

		// 날짜 변환
		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		// ✅ 서비스에서 List<AsTask> 반환하는 오버로드된 메서드 필요
		List<AsTask> asTasks = asTaskService.getFilteredAsList(handlerId, status, dateType, start, end);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=as_task_list.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("AS 목록");

			// 스타일 생성 생략 없이 그대로 유지...
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

			// 헤더 출력
			Row header = sheet.createRow(0);
			String[] titles = { "대리점명", "요청자", "제목", "요청일", "상태", "배정팀", "담당자", "주소", "요청사유", "금액", "비고" };
			for (int i = 0; i < titles.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(titles[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, (i == 7 || i == 8 || i == 10) ? 10000 : 5000);
			}

			// 데이터 출력
			int rowIdx = 1;
			for (AsTask task : asTasks) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				row.createCell(0).setCellValue(task.getRequestedBy().getCompany().getCompanyName());
				row.getCell(0).setCellStyle(borderedStyle);

				row.createCell(1).setCellValue(task.getRequestedBy().getName());
				row.getCell(1).setCellStyle(borderedStyle);

				row.createCell(2).setCellValue(task.getSubject());
				row.getCell(2).setCellStyle(borderedStyle);

				row.createCell(3).setCellValue(task.getRequestedAt() != null ? task.getRequestedAt().toString() : "");
				row.getCell(3).setCellStyle(borderedStyle);

				row.createCell(4).setCellValue(task.getStatus() != null ? task.getStatus().name() : "");
				row.getCell(4).setCellStyle(borderedStyle);

				row.createCell(5).setCellValue(task.getAssignedTeam() != null ? task.getAssignedTeam().getName() : "");
				row.getCell(5).setCellStyle(borderedStyle);

				row.createCell(6)
						.setCellValue(task.getAssignedHandler() != null ? task.getAssignedHandler().getName() : "");
				row.getCell(6).setCellStyle(borderedStyle);

				row.createCell(7).setCellValue(task.getRoadAddress() + " " + task.getDetailAddress());
				row.getCell(7).setCellStyle(wrapStyle);

				row.createCell(8).setCellValue(task.getReason() != null ? task.getReason() : "");
				row.getCell(8).setCellStyle(wrapStyle);

				row.createCell(9).setCellValue(task.getPrice());
				row.getCell(9).setCellStyle(borderedStyle);

				row.createCell(10).setCellValue(task.getAsComment() != null ? task.getAsComment() : "");
				row.getCell(10).setCellStyle(wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
	}

	@PostMapping("/asUpdate/{id}")
	public String updateAsTask(@PathVariable Long id, @RequestParam(required = false) Integer price,
			@RequestParam String status, @RequestParam(required = false) Long assignedHandlerId) {

		asTaskService.updateAsTask(id, price, status, assignedHandlerId);

		return "redirect:/management/asDetail/" + id;
	}

	@GetMapping("/productionList")
	public String productionListPage(
	        @RequestParam(required = false) Long categoryId,
	        @RequestParam(required = false) String status,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	        @RequestParam(required = false) String dateType,
	        Pageable pageable,
	        Model model) {

	    TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

	    LocalDate startD = (startDate != null) ? startDate : LocalDate.now();
	    LocalDate endD = (endDate != null) ? endDate : LocalDate.now().plusDays(1);
	    LocalDateTime start = startD.atStartOfDay();
	    LocalDateTime end = endD.atTime(LocalTime.MAX);

	    OrderStatus parsedStatus;
	    if (status == null) {
	        parsedStatus = OrderStatus.CONFIRMED;
	        status = OrderStatus.CONFIRMED.name();
	    } else if (status.isBlank()) {
	        parsedStatus = null;
	    } else {
	        try {
	            parsedStatus = OrderStatus.valueOf(status);
	        } catch (IllegalArgumentException e) {
	            parsedStatus = OrderStatus.CONFIRMED;
	            status = OrderStatus.CONFIRMED.name();
	        }
	    }

	    Page<Order> orders = orderStatusService.getOrders(start, end, category, parsedStatus, dateType, pageable);

	    model.addAttribute("orders", orders);
	    model.addAttribute("categoryId", categoryId);
	    model.addAttribute("status", status);
	    model.addAttribute("startDate", startD);
	    model.addAttribute("endDate", endD);
	    model.addAttribute("dateType", dateType);
	    model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
	    model.addAttribute("orderStatusList", OrderStatus.values());

	    return "administration/management/production/productionList";
	}

	@GetMapping("/productionList/excel")
	public void downloadProductionListExcel(
	        @RequestParam(required = false) Long categoryId,
	        @RequestParam(required = false) String status,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	        @RequestParam(required = false) String dateType,
	        HttpServletResponse response) throws IOException {

	    TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

	    LocalDate startD = (startDate != null) ? startDate : LocalDate.now();
	    LocalDate endD = (endDate != null) ? endDate : LocalDate.now().plusDays(1);
	    LocalDateTime start = startD.atStartOfDay();
	    LocalDateTime end = endD.atTime(LocalTime.MAX);

	    OrderStatus parsedStatus = null;
	    if (status != null && !status.isBlank()) {
	        try {
	            parsedStatus = OrderStatus.valueOf(status);
	        } catch (IllegalArgumentException ignored) {
	        }
	    }

	    List<Order> orders = orderStatusService.getAllOrders(start, end, category, parsedStatus, dateType);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=production_task_orders.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Production Orders");

			// 스타일 정의
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

			int rowIdx = 0;
			Long lastTaskId = null;

			for (Order order : orders) {
				Task task = order.getTask();

				if (task != null && !task.getId().equals(lastTaskId)) {
					// Task 구분 행 (병합)
					Row labelRow = sheet.createRow(rowIdx++);
					labelRow.setHeightInPoints(20);
					Cell labelCell = labelRow.createCell(0);
					labelCell
							.setCellValue("[대리점명] " + task.getRequestedBy().getCompany().getCompanyName() + " / [요청자명] "
									+ task.getRequestedBy().getName() + " / [발주일] " + task.getCreatedAt().toString());
					labelCell.setCellStyle(headerStyle);
					sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));

					// Order 테이블 헤더
					Row header = sheet.createRow(rowIdx++);
					String[] titles = { "배송지 주소", "수량", "가격", "배송희망일", "배송수단", "배송담당자", "상세사항" };
					for (int i = 0; i < titles.length; i++) {
						Cell cell = header.createCell(i);
						cell.setCellValue(titles[i]);
						cell.setCellStyle(headerStyle);
						sheet.setColumnWidth(i, (i == 6) ? 12000 : 5000);
					}

					lastTaskId = task.getId();
				}

				// Order 내용 행
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(100);

				row.createCell(0).setCellValue(order.getRoadAddress() + " " + order.getDetailAddress());
				row.getCell(0).setCellStyle(borderedStyle);

				row.createCell(1).setCellValue(order.getQuantity());
				row.getCell(1).setCellStyle(borderedStyle);

				row.createCell(2).setCellValue(order.getProductCost());
				row.getCell(2).setCellStyle(borderedStyle);

				row.createCell(3).setCellValue(
						order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : "");
				row.getCell(3).setCellStyle(borderedStyle);

				row.createCell(4).setCellValue(
						order.getDeliveryMethod() != null ? order.getDeliveryMethod().getMethodName() : "");
				row.getCell(4).setCellStyle(borderedStyle);

				row.createCell(5).setCellValue(
						order.getAssignedDeliveryHandler() != null ? order.getAssignedDeliveryHandler().getName() : "");
				row.getCell(5).setCellStyle(borderedStyle);

				// 상세사항 구성
				OrderItem item = order.getOrderItem();
				StringBuilder detail = new StringBuilder();

				if (order.getProductCategory() != null) {
					detail.append("카테고리: ").append(order.getProductCategory().getName()).append("\n");
				}

				if (item != null) {
					detail.append("제품명: ").append(item.getProductName()).append("\n");

					if (item.getOptionJson() != null && !item.getOptionJson().isBlank()) {
						try {
							Map<String, String> optionMap = objectMapper.readValue(item.getOptionJson(),
									new TypeReference<>() {
									});
							int count = 0;
							for (Map.Entry<String, String> entry : optionMap.entrySet()) {
								detail.append(entry.getKey()).append(": ").append(entry.getValue());
								count++;
								if (count % 5 == 0) {
									detail.append("\n");
								} else {
									detail.append(" / ");
								}
							}
							if (!detail.toString().endsWith("\n")) {
								detail.setLength(detail.length() - 3); // 마지막 " / " 제거
							}
						} catch (Exception e) {
							detail.append("[옵션 파싱 실패]");
						}
					}
				}

				Cell detailCell = row.createCell(6);
				detailCell.setCellValue(detail.toString().trim());
				detailCell.setCellStyle(wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
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
	public String deliveryListPage(
	    @RequestParam(required = false) Long categoryId,
	    @RequestParam(required = false) String status,
	    @RequestParam(required = false) String dateType, // preferred or created
	    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	    Pageable pageable, Model model) {

	    // 카테고리 처리
	    TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

	    // 날짜 범위 처리
	    LocalDateTime from;
	    LocalDateTime to;

	    if (startDate != null && endDate != null) {
	        from = startDate.atStartOfDay();
	        to = endDate.atTime(LocalTime.MAX);
	    } else if (startDate != null) {
	        from = startDate.atStartOfDay();
	        to = LocalDateTime.of(9999, 12, 31, 23, 59, 59); // 사실상 무제한 미래
	    } else if (endDate != null) {
	        from = LocalDateTime.of(1970, 1, 1, 0, 0, 0); // 과거 전체 포함
	        to = endDate.atTime(LocalTime.MAX);
	    } else {
	        LocalDate today = LocalDate.now();
	        from = today.atStartOfDay();
	        to = today.atTime(LocalTime.MAX);
	    }

	    // 상태 처리
	    OrderStatus parsedStatus;
	    if (status == null) {
	        parsedStatus = OrderStatus.PRODUCTION_DONE;
	        status = OrderStatus.PRODUCTION_DONE.name();
	    } else if (status.isBlank()) {
	        parsedStatus = null;
	    } else {
	        try {
	            parsedStatus = OrderStatus.valueOf(status);
	        } catch (IllegalArgumentException e) {
	            parsedStatus = OrderStatus.PRODUCTION_DONE;
	            status = OrderStatus.PRODUCTION_DONE.name();
	        }
	    }

	    // 날짜 기준 타입 처리
	    String finalDateType = (dateType == null || dateType.isBlank()) ? "preferred" : dateType;

	    // 주문 리스트 조회
	    Page<Order> orders = orderStatusService.getOrders(
	        from, to, category, parsedStatus, finalDateType, pageable);

	    // 모델에 값 전달
	    model.addAttribute("orders", orders);
	    model.addAttribute("categoryId", categoryId);
	    model.addAttribute("status", status);
	    model.addAttribute("dateType", finalDateType);
	    model.addAttribute("startDate", startDate); // 입력 값 그대로 전달
	    model.addAttribute("endDate", endDate);     // 입력 값 그대로 전달
	    model.addAttribute("categories", memberRepository.findByTeamName("배송팀"));
	    model.addAttribute("orderStatusList", OrderStatus.values());

	    return "administration/management/delivery/deliveryList";
	}

	@GetMapping("/deliveryList/excel")
	public void downloadDeliveryListExcel(
	    @RequestParam(required = false) Long categoryId,
	    @RequestParam(required = false) String status,
	    @RequestParam(required = false) String dateType,
	    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	    HttpServletResponse response) throws IOException {

	    TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

	    LocalDateTime from;
	    LocalDateTime to;

	    if (startDate != null && endDate != null) {
	        from = startDate.atStartOfDay();
	        to = endDate.atTime(LocalTime.MAX);
	    } else if (startDate != null) {
	        from = startDate.atStartOfDay();
	        to = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
	    } else if (endDate != null) {
	        from = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
	        to = endDate.atTime(LocalTime.MAX);
	    } else {
	        LocalDate today = LocalDate.now();
	        from = today.atStartOfDay();
	        to = today.atTime(LocalTime.MAX);
	    }

	    OrderStatus parsedStatus = null;
	    if (status != null && !status.isBlank()) {
	        try {
	            parsedStatus = OrderStatus.valueOf(status);
	        } catch (IllegalArgumentException ignored) {
	        }
	    } else {
	        parsedStatus = OrderStatus.PRODUCTION_DONE;
	    }

	    String finalDateType = (dateType == null || dateType.isBlank()) ? "preferred" : dateType;

	    List<Order> orders = orderStatusService.getAllOrders(from, to, category, parsedStatus, finalDateType);

	    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	    response.setHeader("Content-Disposition", "attachment; filename=delivery_list.xlsx");

	    try (Workbook workbook = new XSSFWorkbook()) {
	        Sheet sheet = workbook.createSheet("배송 리스트");

	        // ... (기존 스타일 및 헤더 작성 부분은 그대로 유지)

	        // 데이터 출력
	        int rowIdx = 1;
	        for (Order order : orders) {
	            Row row = sheet.createRow(rowIdx++);
	            row.setHeightInPoints(80);

	            row.createCell(0).setCellValue(order.getTask().getRequestedBy().getCompany().getCompanyName());
	            row.createCell(1).setCellValue(order.getTask().getRequestedBy().getName());
	            row.createCell(2).setCellValue(order.getRoadAddress() + " " + order.getDetailAddress());
	            row.createCell(3).setCellValue(order.getQuantity());
	            row.createCell(4).setCellValue(order.getProductCost());
	            row.createCell(5).setCellValue(
	                order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : "");
	            row.createCell(6).setCellValue(order.getStatus().name());

	            // 제품 상세
	            StringBuilder detail = new StringBuilder();
	            if (order.getProductCategory() != null) {
	                detail.append("카테고리: ").append(order.getProductCategory().getName()).append("\n");
	            }
	            OrderItem item = order.getOrderItem();
	            if (item != null) {
	                detail.append("제품명: ").append(item.getProductName()).append("\n");
	                if (item.getOptionJson() != null && !item.getOptionJson().isBlank()) {
	                    try {
	                        Map<String, String> optionMap = objectMapper.readValue(item.getOptionJson(), new TypeReference<>() {});
	                        int count = 0;
	                        for (Map.Entry<String, String> entry : optionMap.entrySet()) {
	                            detail.append(entry.getKey()).append(": ").append(entry.getValue());
	                            count++;
	                            if (count % 5 == 0) detail.append("\n");
	                            else detail.append(" / ");
	                        }
	                        if (!detail.toString().endsWith("\n")) {
	                            detail.setLength(detail.length() - 3); // 마지막 " / " 제거
	                        }
	                    } catch (Exception e) {
	                        detail.append("[옵션 파싱 실패]");
	                    }
	                }
	            }
	            Cell detailCell = row.createCell(7);
	            detailCell.setCellValue(detail.toString().trim());
	        }

	        workbook.write(response.getOutputStream());
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
			@PageableDefault(size = 10) Pageable pageable, Model model) {

		Page<Company> companies = companyService.getCompanyList(keyword, searchType, pageable);
		model.addAttribute("companies", companies);
		model.addAttribute("keyword", keyword);
		model.addAttribute("searchType", searchType);
		return "administration/member/client/clientList";
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

	@GetMapping("/employeeList")
	public String employeeList(@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "team", required = false) String team,
			@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			Model model) {

		Page<Member> employeePage = memberService.searchEmployees(name, team, pageable);

		model.addAttribute("employeePage", employeePage);
		model.addAttribute("name", name);
		model.addAttribute("team", team);
		return "administration/member/employee/employeeList";
	}

	@GetMapping("/employeeDetail/{id}")
	public String employeeDetail(@PathVariable Long id, Model model) {
		Member member = memberRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 직원이 존재하지 않습니다."));

		// 권한 체크 (INTERNAL_EMPLOYEE 또는 MANAGEMENT만 허용)
		if (!(member.getRole() == MemberRole.INTERNAL_EMPLOYEE || member.getRole() == MemberRole.MANAGEMENT)) {
			throw new IllegalArgumentException("직원만 조회 가능합니다.");
		}

		model.addAttribute("member", member);
		return "administration/member/employee/employeeDetail";
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

	@PostMapping("/employeeUpdate")
	@ResponseBody
	public String employeeUpdate() {

		return "success";
	}
}
