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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.dto.ApiResponse;
import com.dev.HiddenBATHAuto.dto.MemberSaveDTO;
import com.dev.HiddenBATHAuto.dto.NonStandardOrderCompanyOptionDto;
import com.dev.HiddenBATHAuto.dto.as.CompanySearchItemDto;
import com.dev.HiddenBATHAuto.dto.client.AdminClientApiResponse;
import com.dev.HiddenBATHAuto.dto.client.AdminClientCompanyUpdateRequest;
import com.dev.HiddenBATHAuto.dto.client.AdminClientMemberUpdateRequest;
import com.dev.HiddenBATHAuto.dto.client.CompanyListRowDto;
import com.dev.HiddenBATHAuto.dto.employee.EmployeeUpdateResult;
import com.dev.HiddenBATHAuto.dto.employeeDetail.EmployeeUpdateRequest;
import com.dev.HiddenBATHAuto.dto.employeeDetail.MemberRegionSimpleDTO;
import com.dev.HiddenBATHAuto.dto.employeeDetail.RegionBulkSaveRequest;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.GroupRow;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.SearchCondition;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.SearchResult;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListCompanyDeliveryAddressOptionDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListCompanyMemberOptionDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListCompanyOptionDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListCompanyOrdererInfoOptionDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListOrderImageDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListOrderRowDto;
import com.dev.HiddenBATHAuto.enums.AsBillingTarget;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.City;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
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
import com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyOrdererInfoRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;
import com.dev.HiddenBATHAuto.service.MemberAdminService;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.auth.AddressRegionResolver;
import com.dev.HiddenBATHAuto.service.auth.CompanyService;
import com.dev.HiddenBATHAuto.service.auth.MemberManagementService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.client.AdminClientDetailService;
import com.dev.HiddenBATHAuto.service.order.NonStandardOrderItemService;
import com.dev.HiddenBATHAuto.service.order.OrderChangeAuditService;
import com.dev.HiddenBATHAuto.service.order.NonStandardTaskListViewService;
import com.dev.HiddenBATHAuto.service.order.OrderStatusService;
import com.dev.HiddenBATHAuto.service.management.delivery.ManagementDeliveryListExcelService;
import com.dev.HiddenBATHAuto.service.management.delivery.ManagementDeliveryListService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/management")
@RequiredArgsConstructor
public class ManagementController {

	public record AdminClientDeliveryAddressSaveRequest(
			String zipCode,
			String doName,
			String siName,
			String guName,
			String roadAddress,
			String detailAddress
	) {
	}

	public record AdminClientDeliveryAddressItem(
			Long id,
			String zipCode,
			String doName,
			String siName,
			String guName,
			String roadAddress,
			String detailAddress,
			String fullAddress
	) {
	}

	public record AdminClientDeliveryAddressResponse(
			boolean success,
			String message,
			List<AdminClientDeliveryAddressItem> addresses
	) {
	}

	private final TaskRepository taskRepository;
	private final OrderRepository orderRepository;
	private final MemberRepository memberRepository;
	private final DeliveryMethodRepository deliveryMethodRepository;
	private final TeamCategoryRepository teamCategoryRepository;

	private final TeamRepository teamRepository;
	private final CompanyRepository companyRepository;
	private final CompanyDeliveryAddressRepository companyDeliveryAddressRepository;
	private final CompanyOrdererInfoRepository companyOrdererInfoRepository;
	private final ProvinceRepository provinceRepository;
	private final OrderImageRepository orderImageRepository;

	private final MemberService memberService;
	private final AddressRegionResolver addressRegionResolver;
	private final CompanyService companyService;
	private final ObjectMapper objectMapper;
	private final MemberManagementService memberMgmtService;
	private final NonStandardOrderItemService nonStandardOrderItemService;
	private final AsTaskService asTaskService;
	private final OrderStatusService orderStatusService;
	// ✅ 추가 서비스
	private final MemberAdminService memberAdminService;
	private final AdminClientDetailService adminClientDetailService;

	private final NonStandardTaskListViewService nonStandardTaskListViewService;
	private final OrderChangeAuditService orderChangeAuditService;
	private final ManagementDeliveryListService managementDeliveryListService;
	private final ManagementDeliveryListExcelService managementDeliveryListExcelService;

	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");


	/*
	 * ========================================================= nonStandardTaskList
	 * - 목록은 가볍게 조회 - 넓게보기는 오더 1건 기준 AJAX fragment 로딩 - 대리점 변경 시 company-options
	 * AJAX로 새 회사의 신청자/배송지/주문자 정보 로딩
	 * =========================================================
	 */

	public record NonStandardTaskListCompanyOptionsResponse(boolean success,
			NonStandardTaskListCompanyOptionDto company, List<NonStandardTaskListCompanyMemberOptionDto> members,
			List<NonStandardTaskListCompanyDeliveryAddressOptionDto> deliveryAddresses,
			List<NonStandardTaskListCompanyOrdererInfoOptionDto> orderers) {
	}

	public record NonStandardTaskBulkConfirmOrderRequest(Long orderId, Long deliveryHandlerId) {
	}

	public record NonStandardTaskBulkConfirmRequest(List<NonStandardTaskBulkConfirmOrderRequest> orders) {
	}

	private record NonStandardTaskListFilterItem(String label, String value) {
	}

	@GetMapping("/nonStandardTaskList")
	public String nonStandardTaskList(@RequestParam(required = false, defaultValue = "") String keyword,
			@RequestParam(required = false) String orderIdFrom,
			@RequestParam(required = false) String orderIdTo,
			@RequestParam(required = false) String orderId,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false, defaultValue = "all") String dateCriteria,
			@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
			@RequestParam(required = false, defaultValue = "all") String productCategoryId,
			@RequestParam(required = false, defaultValue = "REQUESTED") String orderStatus,
			@RequestParam(required = false, defaultValue = "all") String standard,
			@RequestParam(required = false) String sortState,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			@PageableDefault(size = 10) Pageable pageable, HttpServletRequest request, Model model) {
		String finalDateCriteria = normalizeDateCriteria(dateCriteria);

		DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);

		Boolean standardBool = parseStandardOrNull(standard);
		Long categoryId = parseLongOrNullAllowAll(productCategoryId);
		OrderStatus statusEnum = parseOrderStatusOrNullWithDefault(orderStatus, OrderStatus.REQUESTED);
		OrderIdRangeFilter orderIdRange = resolveOrderIdRange(orderIdFrom, orderIdTo, orderId);
		Long finalOrderIdFrom = orderIdRange.from();
		Long finalOrderIdTo = orderIdRange.to();
		String finalProductName = normalizeNullableSearchText(productName);
		String finalKeyword = normalizeNullableSearchText(keyword);

		List<NonStandardTaskListSortCriterion> activeSortCriteria =
				resolveNonStandardTaskListSortCriteria(sortState, sortField, sortDir);
		Sort resolvedSort = buildNonStandardTaskListSort(activeSortCriteria);

		Pageable sortedPageable = PageRequest.of(
				Math.max(pageable.getPageNumber(), 0),
				pageable.getPageSize(),
				resolvedSort
		);

		Page<Order> orders = orderRepository.findFilteredOrdersWithOrderIdRangeAndProductName(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range.getStart(),
				range.getEnd(),
				categoryId,
				statusEnum,
				standardBool,
				sortedPageable
		);

		/*
		 * 속도 개선: 목록에서는 화면에 바로 보이는 데이터만 DTO로 변환합니다. 상세 수정용 회사/회원/주소/주문자 데이터는 넓게보기 AJAX에서
		 * 오더 단위로 가져옵니다.
		 */
		Page<NonStandardTaskListOrderRowDto> orderRows = orders.map(nonStandardTaskListViewService::toRow);

        List<Long> currentOrderIds = orders.getContent().stream()
                .map(Order::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        // 상세/일괄보기는 등록 이력까지 기존 그대로 사용합니다.
        model.addAttribute("latestOrderChangeMap",
                orderChangeAuditService.getLatestChangeMap(currentOrderIds));
        // 일반 목록의 한 줄 변경표시에서는 신규 등록 이벤트만 제외합니다.
        model.addAttribute("latestListOrderChangeMap",
                orderChangeAuditService.getLatestListChangeMap(currentOrderIds));
        model.addAttribute("productionCheckAggregateMap",
                orderChangeAuditService.getCheckAggregateMap(currentOrderIds, OrderWorkArea.PRODUCTION));

		int currentPage1 = orders.getPageable().getPageNumber() + 1;
		int startPageNum = Math.max(1, currentPage1 - 4);
		int endPageNum = Math.min(orders.getTotalPages(), currentPage1 + 4);

		model.addAttribute("orders", orders);
		model.addAttribute("orderRows", orderRows);

		/*
		 * 일괄보기는 최초 페이지 로딩 때 전체 카드를 만들지 않습니다. 버튼 클릭 시 /nonStandardTaskList/bulk-fragment
		 * 에서 별도 로딩합니다.
		 */
		model.addAttribute("bulkOrderRows", List.of());
		// 일괄보기 대상은 검색 전체 건수가 아니라 현재 페이지에 실제 표시된 주문만 사용합니다.
		model.addAttribute("bulkOrderCount", orders.getNumberOfElements());

		model.addAttribute("startPage", startPageNum);
		model.addAttribute("endPage", endPageNum);

		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("orderStatuses", OrderStatus.values());

		/*
		 * 상세 수정용 배송수단/회사/주소 데이터는 order-detail-fragment에서 내려갑니다.
		 * 배송팀 멤버는 고객 발주 일괄 컨펌 모달의 담당자 선택지로 사용하므로 활성 멤버만 제공합니다.
		 */
		model.addAttribute("deliveryMethods", List.of());
		model.addAttribute("deliveryTeamMembers",
				memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAsc("배송팀"));
		model.addAttribute("companyOptions", List.of());
		model.addAttribute("companyMemberOptions", List.of());
		model.addAttribute("companyDeliveryAddressOptions", List.of());
		model.addAttribute("companyOrdererInfoOptions", List.of());

		model.addAttribute("keyword", finalKeyword != null ? finalKeyword : "");
		model.addAttribute("orderIdFrom", finalOrderIdFrom != null ? String.valueOf(finalOrderIdFrom) : "");
		model.addAttribute("orderIdTo", finalOrderIdTo != null ? String.valueOf(finalOrderIdTo) : "");
		// 기존 orderId 단건 URL과 범위 미지원 외부 다운로드 링크 호환용입니다.
		model.addAttribute("orderId", java.util.Objects.equals(finalOrderIdFrom, finalOrderIdTo)
				? (finalOrderIdFrom != null ? String.valueOf(finalOrderIdFrom) : "")
				: "");
		model.addAttribute("productName", finalProductName != null ? finalProductName : "");
		model.addAttribute("dateCriteria", finalDateCriteria);

		model.addAttribute("startDate", range.getStartDateStr());
		model.addAttribute("endDate", range.getEndDateStr());
		model.addAttribute("startDateStr", range.getStartDateStr());
		model.addAttribute("endDateStr", range.getEndDateStr());

		model.addAttribute("productCategoryId", (productCategoryId == null) ? "all" : productCategoryId);
		model.addAttribute("orderStatus", (orderStatus == null) ? OrderStatus.REQUESTED.name() : orderStatus);
		model.addAttribute("standard", (standard == null) ? "all" : standard);

		String normalizedSortState = serializeNonStandardTaskListSortState(activeSortCriteria);
		Map<String, String> activeSortDirections = activeSortCriteria.stream()
				.collect(Collectors.toMap(
						NonStandardTaskListSortCriterion::field,
						NonStandardTaskListSortCriterion::direction,
						(left, right) -> right,
						LinkedHashMap::new
				));

		/*
		 * sortField/sortDir는 기존 엑셀 및 다른 링크 호환을 위해 첫 번째 정렬값만 유지합니다.
		 * 실제 목록 정렬 상태는 sortState가 전체를 관리합니다.
		 */
		String firstSortField = activeSortCriteria.isEmpty() ? "" : activeSortCriteria.get(0).field();
		String firstSortDir = activeSortCriteria.isEmpty() ? "" : activeSortCriteria.get(0).direction();

		model.addAttribute("sortState", normalizedSortState);
		model.addAttribute("activeSortDirections", activeSortDirections);
		model.addAttribute("sortField", firstSortField);
		model.addAttribute("sortDir", firstSortDir);
		model.addAttribute("currentListUrl", buildCurrentRequestUrl(request));
		model.addAttribute("pageSize", orders.getSize());

		return "administration/management/order/nonStandard/taskList";
	}

	@GetMapping("/nonStandardTaskList/order-detail-fragment/{orderId}")
	public String nonStandardTaskListOrderDetailFragment(@PathVariable Long orderId,
			@RequestParam(value = "returnUrl", required = false) String returnUrl, Model model) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오더입니다. orderId=" + orderId));

		Map<Long, List<NonStandardTaskListOrderImageDto>> adminImageMap = orderImageRepository
				.findByOrder_IdInAndTypeIgnoreCase(List.of(orderId), "MANAGEMENT").stream()
				.collect(Collectors.groupingBy(image -> image.getOrder().getId(),
						Collectors.mapping(image -> NonStandardTaskListOrderImageDto.builder().id(image.getId())
								.type(image.getType()).filename(image.getFilename()).url(image.getUrl()).build(),
								Collectors.toList())));

		NonStandardTaskListOrderRowDto row = nonStandardTaskListViewService.toRow(order,
				adminImageMap.getOrDefault(orderId, List.of()));

		Long selectedCompanyId = resolveOrderCompanyId(order);

		/*
		 * 회사 select는 대리점 변경 가능성이 있으므로 전체 목록을 내려줍니다. 단, 여기서는 대표회원/멤버/배송지/주문자 전체 조회를 하지
		 * 않습니다.
		 */
		List<Company> companies = companyRepository.findAll();

		model.addAttribute("row", row);
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("deliveryTeamMembers",
				memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAsc("배송팀"));
		model.addAttribute("productionTeamCategories", teamCategoryRepository.findByTeamName("생산팀"));

		/*
		 * 속도 개선 핵심: - 회사 select용 전체 회사 목록만 가볍게 제공 - 현재 오더의 회사에 해당하는 멤버/배송지/주문자만 초기 제공 -
		 * 다른 대리점 선택 시 company-options AJAX로 새로 가져옴
		 */
		model.addAttribute("companyOptions", buildOrderListCompanyOptionsFast(companies));
		model.addAttribute("companyMemberOptions", buildOrderListCompanyMemberOptionsByCompanyId(selectedCompanyId));
		model.addAttribute("companyDeliveryAddressOptions",
				buildOrderListCompanyDeliveryAddressOptionsByCompanyId(selectedCompanyId));
		model.addAttribute("companyOrdererInfoOptions",
				buildOrderListCompanyOrdererInfoOptionsByCompanyId(selectedCompanyId));

		model.addAttribute("currentListUrl",
				isSafeNonStandardTaskListReturnUrl(returnUrl) ? returnUrl.trim() : "/management/nonStandardTaskList");
        model.addAttribute("orderChangeHistory", orderChangeAuditService.getOrderHistory(orderId, 50));
        model.addAttribute("productionMemberCheckStates",
                orderChangeAuditService.getMemberCheckStates(orderId, OrderWorkArea.PRODUCTION));

		return "administration/management/order/nonStandard/taskListOrderDetailFragment :: orderDetailRow";
	}

	/*
	 * 대리점 변경 AJAX 넓게보기 안에서 대리점을 바꿨을 때, 새 대리점의 신청자/등록 배송지/주문자/기본주소를 다시 내려줍니다.
	 */
	@GetMapping("/nonStandardTaskList/company-options/{companyId}")
	@ResponseBody
	public ResponseEntity<NonStandardTaskListCompanyOptionsResponse> nonStandardTaskListCompanyOptions(
			@PathVariable Long companyId) {
		Company company = companyRepository.findById(companyId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대리점입니다. companyId=" + companyId));

		NonStandardTaskListCompanyOptionsResponse response = new NonStandardTaskListCompanyOptionsResponse(true,
				buildOrderListCompanyOption(company), buildOrderListCompanyMemberOptionsByCompanyId(companyId),
				buildOrderListCompanyDeliveryAddressOptionsByCompanyId(companyId),
				buildOrderListCompanyOrdererInfoOptionsByCompanyId(companyId));

		return ResponseEntity.ok(response);
	}

	@PostMapping("/nonStandardTaskList/bulk-confirm")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> nonStandardTaskListBulkConfirm(
			@RequestBody NonStandardTaskBulkConfirmRequest request,
            Authentication authentication) {
		try {
			if (request == null || request.orders() == null || request.orders().isEmpty()) {
				throw new IllegalArgumentException("컨펌 처리할 오더를 하나 이상 선택해 주세요.");
			}

			List<Long> orderIds = new ArrayList<>();
			LinkedHashMap<Long, Long> deliveryHandlerIdByOrderId = new LinkedHashMap<>();

			for (NonStandardTaskBulkConfirmOrderRequest orderRequest : request.orders()) {
				if (orderRequest == null || orderRequest.orderId() == null || orderRequest.orderId() <= 0) {
					throw new IllegalArgumentException("올바르지 않은 오더 ID가 포함되어 있습니다.");
				}

				if (deliveryHandlerIdByOrderId.containsKey(orderRequest.orderId())) {
					throw new IllegalArgumentException(
							orderRequest.orderId() + "번 오더가 일괄 컨펌 요청에 중복 포함되어 있습니다.");
				}

				orderIds.add(orderRequest.orderId());
				deliveryHandlerIdByOrderId.put(
						orderRequest.orderId(),
						orderRequest.deliveryHandlerId() != null && orderRequest.deliveryHandlerId() > 0
								? orderRequest.deliveryHandlerId()
								: null
				);
			}

            String actorUsername = resolveAuthenticatedUsername(authentication);
            String actorDisplayName = actorUsername;
            Long actorMemberId = null;

            if (authentication != null && authentication.getPrincipal() instanceof PrincipalDetails principalDetails
                    && principalDetails.getMember() != null) {
                Member actor = principalDetails.getMember();
                actorMemberId = actor.getId();
                if (actor.getName() != null && !actor.getName().isBlank()) {
                    actorDisplayName = actor.getName().trim();
                }
            }

			int updatedCount = orderStatusService.bulkConfirmRequestedOrders(
					orderIds,
					deliveryHandlerIdByOrderId,
                    actorUsername,
                    actorDisplayName,
                    actorMemberId
			);

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", true);
			response.put("updatedCount", updatedCount);
			response.put("message", updatedCount + "건을 승인 완료로 변경했습니다.");
			return ResponseEntity.ok(response);

		} catch (IllegalArgumentException | IllegalStateException e) {
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success", false);
			response.put("message", e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@GetMapping("/nonStandardTaskList/bulk-fragment")
	public String nonStandardTaskListBulkFragment(@RequestParam(required = false, defaultValue = "") String keyword,
			@RequestParam(required = false) String orderIdFrom,
			@RequestParam(required = false) String orderIdTo,
			@RequestParam(required = false) String orderId,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false, defaultValue = "all") String dateCriteria,
			@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
			@RequestParam(required = false, defaultValue = "all") String productCategoryId,
			@RequestParam(required = false, defaultValue = "REQUESTED") String orderStatus,
			@RequestParam(required = false, defaultValue = "all") String standard,
			@RequestParam(required = false) String sortState,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			@PageableDefault(size = 10) Pageable pageable, Model model) {
		String finalDateCriteria = normalizeDateCriteria(dateCriteria);

		DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);

		Boolean standardBool = parseStandardOrNull(standard);
		Long categoryId = parseLongOrNullAllowAll(productCategoryId);
		OrderStatus statusEnum = parseOrderStatusOrNullWithDefault(orderStatus, OrderStatus.REQUESTED);
		OrderIdRangeFilter orderIdRange = resolveOrderIdRange(orderIdFrom, orderIdTo, orderId);
		Long finalOrderIdFrom = orderIdRange.from();
		Long finalOrderIdTo = orderIdRange.to();
		String finalProductName = normalizeNullableSearchText(productName);
		String finalKeyword = normalizeNullableSearchText(keyword);

		/*
		 * 일괄보기 역시 메인 목록과 완전히 동일한 page/size/정렬을 사용합니다.
		 * 검색조건 전체를 List로 다시 조회하면 최초 화면에서 수천~수만 건이 한 번에 렌더링될 수 있으므로,
		 * 현재 페이지에 실제 표시되는 주문만 DTO로 변환합니다.
		 */
		List<NonStandardTaskListSortCriterion> activeSortCriteria =
				resolveNonStandardTaskListSortCriteria(sortState, sortField, sortDir);
		Sort resolvedSort = buildNonStandardTaskListSort(activeSortCriteria);

		Pageable sortedPageable = PageRequest.of(
				Math.max(pageable.getPageNumber(), 0),
				pageable.getPageSize(),
				resolvedSort
		);

		Page<Order> bulkOrderPage = orderRepository.findFilteredOrdersWithOrderIdRangeAndProductName(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range.getStart(),
				range.getEnd(),
				categoryId,
				statusEnum,
				standardBool,
				sortedPageable
		);

		List<Order> bulkOrders = bulkOrderPage.getContent();
		List<NonStandardTaskListOrderRowDto> bulkOrderRows = nonStandardTaskListViewService.toBulkRows(bulkOrders);

		model.addAttribute("bulkOrderRows", bulkOrderRows);
		List<Long> bulkOrderIds = bulkOrders.stream().map(Order::getId).filter(java.util.Objects::nonNull).toList();
		model.addAttribute("latestOrderChangeMap", orderChangeAuditService.getLatestChangeMap(bulkOrderIds));
		model.addAttribute("productionCheckAggregateMap",
				orderChangeAuditService.getCheckAggregateMap(bulkOrderIds, OrderWorkArea.PRODUCTION));

		return "administration/management/order/nonStandard/taskList :: bulkOrderCards";
	}

	private Long resolveOrderCompanyId(Order order) {
		if (order == null || order.getTask() == null || order.getTask().getRequestedBy() == null) {
			return null;
		}

		Company company = order.getTask().getRequestedBy().getCompany();
		return company != null ? company.getId() : null;
	}

	private NonStandardTaskListCompanyOptionDto buildOrderListCompanyOption(Company company) {
		if (company == null) {
			return null;
		}

		String representativeName = memberRepository
				.findCompanyMembersByRole(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE, PageRequest.of(0, 1))
				.stream().findFirst().map(Member::getName).orElse("");

		return NonStandardTaskListCompanyOptionDto.builder().companyId(company.getId())
				.companyName(orderListText(company.getCompanyName()))
				.representativeName(orderListText(representativeName)).zipCode(orderListText(company.getZipCode()))
				.doName(orderListText(company.getDoName())).siName(orderListText(company.getSiName()))
				.guName(orderListText(company.getGuName())).roadAddress(orderListText(company.getRoadAddress()))
				.detailAddress(orderListText(company.getDetailAddress()))
				.fullAddress(buildOrderListFullAddress(company.getZipCode(), company.getDoName(), company.getSiName(),
						company.getGuName(), company.getRoadAddress(), company.getDetailAddress()))
				.build();
	}

	private List<NonStandardTaskListCompanyOptionDto> buildOrderListCompanyOptionsFast(List<Company> companies) {
		if (companies == null || companies.isEmpty()) {
			return List.of();
		}

		return companies.stream()
				.map(company -> NonStandardTaskListCompanyOptionDto.builder().companyId(company.getId())
						.companyName(orderListText(company.getCompanyName())).representativeName("")
						.zipCode(orderListText(company.getZipCode())).doName(orderListText(company.getDoName()))
						.siName(orderListText(company.getSiName())).guName(orderListText(company.getGuName()))
						.roadAddress(orderListText(company.getRoadAddress()))
						.detailAddress(orderListText(company.getDetailAddress()))
						.fullAddress(buildOrderListFullAddress(company.getZipCode(), company.getDoName(),
								company.getSiName(), company.getGuName(), company.getRoadAddress(),
								company.getDetailAddress()))
						.build())
				.toList();
	}

	private List<NonStandardTaskListCompanyMemberOptionDto> buildOrderListCompanyMemberOptionsByCompanyId(
			Long companyId) {
		if (companyId == null) {
			return List.of();
		}

		return memberRepository.findByCompany_Id(companyId).stream()
				.map(member -> new NonStandardTaskListCompanyMemberOptionDto(companyId, member.getId(),
						orderListText(member.getName())))
				.toList();
	}

	private List<NonStandardTaskListCompanyDeliveryAddressOptionDto> buildOrderListCompanyDeliveryAddressOptionsByCompanyId(
			Long companyId) {
		if (companyId == null) {
			return List.of();
		}

		return companyDeliveryAddressRepository.findByCompany_IdOrderByIdAsc(companyId).stream()
				.map(address -> new NonStandardTaskListCompanyDeliveryAddressOptionDto(companyId, address.getId(),
						orderListText(address.getZipCode()), orderListText(address.getDoName()),
						orderListText(address.getSiName()), orderListText(address.getGuName()),
						orderListText(address.getRoadAddress()), orderListText(address.getDetailAddress()),
						buildOrderListFullAddress(address)))
				.toList();
	}

	private List<NonStandardTaskListCompanyOrdererInfoOptionDto> buildOrderListCompanyOrdererInfoOptionsByCompanyId(
			Long companyId) {
		if (companyId == null) {
			return List.of();
		}

		return companyOrdererInfoRepository.findByCompany_IdOrderByIdAsc(companyId).stream()
				.map(ordererInfo -> new NonStandardTaskListCompanyOrdererInfoOptionDto(companyId, ordererInfo.getId(),
						orderListText(ordererInfo.getOrdererName()), orderListText(ordererInfo.getPhone())))
				.toList();
	}

	private String buildOrderListFullAddress(String zipCode, String doName, String siName, String guName,
			String roadAddress, String detailAddress) {
		return DeliveryAddressNormalizationUtil
				.build(zipCode, doName, siName, guName, roadAddress, detailAddress)
				.display();
	}

	private String buildOrderListFullAddress(CompanyDeliveryAddress address) {
		if (address == null) {
			return "";
		}

		return buildOrderListFullAddress(address.getZipCode(), address.getDoName(), address.getSiName(),
				address.getGuName(), address.getRoadAddress(), address.getDetailAddress());
	}

	private String orderListText(String value) {
		return value == null ? "" : value;
	}

	private String orderListWrapIfNotBlank(String value, String prefix, String suffix) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return prefix + value + suffix;
	}

	private String orderListJoinNonBlank(String delimiter, String... values) {
		if (values == null) {
			return "";
		}

		return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank())
				.collect(Collectors.joining(delimiter));
	}

	private String buildCurrentRequestUrl(HttpServletRequest request) {
		String queryString = request.getQueryString();

		if (queryString == null || queryString.isBlank()) {
			return request.getRequestURI();
		}

		return request.getRequestURI() + "?" + queryString;
	}


	private record NonStandardTaskListSortCriterion(String field, String direction) {
	}

	private List<NonStandardTaskListSortCriterion> resolveNonStandardTaskListSortCriteria(
			String sortState,
			String legacySortField,
			String legacySortDir
	) {
		LinkedHashMap<String, String> resolved = new LinkedHashMap<>();

		if (sortState != null && !sortState.isBlank()) {
			String[] tokens = sortState.split("\\|");

			for (String token : tokens) {
				if (token == null || token.isBlank()) {
					continue;
				}

				String[] parts = token.trim().split(":", 2);
				if (parts.length != 2) {
					continue;
				}

				String field = parts[0].trim();
				String direction = normalizeNonStandardTaskListSortDirection(parts[1]);

				if (mapSortFieldToProperty(field) == null || direction == null) {
					continue;
				}

				/*
				 * 같은 필드가 중복 전달되면 최초 우선순위 위치는 유지하고 방향만 최신 값으로 갱신합니다.
				 */
				resolved.put(field, direction);

				if (resolved.size() >= 7) {
					break;
				}
			}
		}

		/*
		 * 기존 sortField/sortDir URL도 깨지지 않도록 단일 정렬로 호환합니다.
		 * sortState가 있으면 sortState가 우선입니다.
		 */
		if (resolved.isEmpty()) {
			String legacyDirection = normalizeNonStandardTaskListSortDirection(legacySortDir);

			if (mapSortFieldToProperty(legacySortField) != null && legacyDirection != null) {
				resolved.put(legacySortField.trim(), legacyDirection);
			}
		}

		List<NonStandardTaskListSortCriterion> criteria = new ArrayList<>();
		resolved.forEach((field, direction) ->
				criteria.add(new NonStandardTaskListSortCriterion(field, direction))
		);
		return criteria;
	}

	private String normalizeNonStandardTaskListSortDirection(String direction) {
		if (direction == null || direction.isBlank()) {
			return null;
		}

		String normalized = direction.trim().toLowerCase(Locale.ROOT);
		return "asc".equals(normalized) || "desc".equals(normalized) ? normalized : null;
	}

	private Sort buildNonStandardTaskListSort(List<NonStandardTaskListSortCriterion> criteria) {
		/*
		 * 사용자 정렬이 없는 상태도 DB 반환 순서가 흔들리지 않도록 최근 발주순을 내부 기본 정렬로 사용합니다.
		 * 이 기본 정렬은 화면의 활성 화살표로 표시하지 않습니다.
		 */
		if (criteria == null || criteria.isEmpty()) {
			return Sort.by(Sort.Direction.DESC, "createdAt")
					.and(Sort.by(Sort.Direction.DESC, "id"));
		}

		Sort result = Sort.unsorted();

		for (NonStandardTaskListSortCriterion criterion : criteria) {
			String property = mapSortFieldToProperty(criterion.field());
			if (property == null) {
				continue;
			}

			Sort.Direction direction = "asc".equals(criterion.direction())
					? Sort.Direction.ASC
					: Sort.Direction.DESC;

			result = result.and(Sort.by(direction, property));
		}

		if (result.isUnsorted()) {
			return Sort.by(Sort.Direction.DESC, "createdAt")
					.and(Sort.by(Sort.Direction.DESC, "id"));
		}

		return result.and(Sort.by(Sort.Direction.DESC, "id"));
	}

	private String serializeNonStandardTaskListSortState(
			List<NonStandardTaskListSortCriterion> criteria
	) {
		if (criteria == null || criteria.isEmpty()) {
			return "";
		}

		return criteria.stream()
				.map(criterion -> criterion.field() + ":" + criterion.direction())
				.collect(Collectors.joining("|"));
	}

	private String mapSortFieldToProperty(String sortField) {
		if (sortField == null || sortField.isBlank()) {
			return null;
		}

		return switch (sortField) {
		case "orderId" -> "id";
		case "agencyName" -> "task.requestedBy.company.companyName";
		case "requesterName" -> "task.requestedBy.name";
		case "productCategoryName" -> "productCategory.name";
		case "standard" -> "standard";
		case "orderDate" -> "createdAt";
		case "preferredDeliveryDate" -> "preferredDeliveryDate";
		case "status" -> "status";
		case "companyName" -> "task.requestedBy.company.companyName";
		default -> null;
		};
	}

	@GetMapping("/nonStandardOrder/print")
	public String printNonStandardOrderList(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String orderIdFrom,
			@RequestParam(required = false) String orderIdTo,
			@RequestParam(required = false) String orderId,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false) String dateCriteria,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate,
			@RequestParam(required = false) String orderStatus,
			@RequestParam(required = false) String productCategoryId,
			@RequestParam(required = false) String standard,
			@RequestParam(required = false) String sortState,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			Model model
	) {
		String finalDateCriteria = normalizeDateCriteria(dateCriteria);
		DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);
		Long categoryId = parseLongOrNullAllowAll(productCategoryId);
		OrderStatus status = parseOrderStatusOrNullWithDefault(orderStatus, null);
		Boolean standardBool = parseStandardOrNull(standard);
		OrderIdRangeFilter orderIdRange = resolveOrderIdRange(orderIdFrom, orderIdTo, orderId);
		Long finalOrderIdFrom = orderIdRange.from();
		Long finalOrderIdTo = orderIdRange.to();
		String finalProductName = normalizeNullableSearchText(productName);
		String finalKeyword = normalizeNullableSearchText(keyword);

		List<Order> orderList = orderRepository.findFilteredOrdersForExcelWithOrderIdRangeAndProductName(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range.getStart(),
				range.getEnd(),
				categoryId,
				status,
				standardBool
		);

		List<NonStandardTaskListSortCriterion> sortCriteria =
				resolveNonStandardTaskListSortCriteria(sortState, sortField, sortDir);
		List<Order> sortedOrders = sortNonStandardTaskListOutputOrders(orderList, sortCriteria);

		model.addAttribute("rows", nonStandardTaskListViewService.toBulkRows(sortedOrders));
		model.addAttribute("filters", buildNonStandardTaskListFilterItems(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range,
				categoryId,
				status,
				standardBool
		));
		model.addAttribute("productNameKeyword", finalProductName != null ? finalProductName : "");
		model.addAttribute("generatedAt", LocalDateTime.now());

		return "administration/management/order/nonStandard/taskListPrint";
	}

	@GetMapping("/nonStandardOrder/excel")
	public void downloadNonStandardOrderExcel(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String orderIdFrom,
			@RequestParam(required = false) String orderIdTo,
			@RequestParam(required = false) String orderId,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false) String dateCriteria,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate,
			@RequestParam(required = false) String orderStatus,
			@RequestParam(required = false) String productCategoryId,
			@RequestParam(required = false) String standard,
			@RequestParam(required = false) String sortState,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			HttpServletResponse response
	) throws IOException {

		String finalDateCriteria = normalizeDateCriteria(dateCriteria);
		DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);
		Long categoryId = parseLongOrNullAllowAll(productCategoryId);
		OrderStatus status = parseOrderStatusOrNullWithDefault(orderStatus, null);
		Boolean standardBool = parseStandardOrNull(standard);
		OrderIdRangeFilter orderIdRange = resolveOrderIdRange(orderIdFrom, orderIdTo, orderId);
		Long finalOrderIdFrom = orderIdRange.from();
		Long finalOrderIdTo = orderIdRange.to();
		String finalProductName = normalizeNullableSearchText(productName);
		String finalKeyword = normalizeNullableSearchText(keyword);

		List<Order> orderList = orderRepository.findFilteredOrdersForExcelWithOrderIdRangeAndProductName(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range.getStart(),
				range.getEnd(),
				categoryId,
				status,
				standardBool
		);

		List<NonStandardTaskListSortCriterion> sortCriteria =
				resolveNonStandardTaskListSortCriteria(sortState, sortField, sortDir);
		orderList = sortNonStandardTaskListOutputOrders(orderList, sortCriteria);

		List<NonStandardTaskListFilterItem> filters = buildNonStandardTaskListFilterItems(
				finalKeyword,
				finalOrderIdFrom,
				finalOrderIdTo,
				finalProductName,
				finalDateCriteria,
				range,
				categoryId,
				status,
				standardBool
		);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=non_standard_orders.xlsx");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("비규격발주");

			Font titleFont = workbook.createFont();
			titleFont.setBold(true);
			titleFont.setFontHeightInPoints((short) 14);

			Font boldFont = workbook.createFont();
			boldFont.setBold(true);

			CellStyle titleStyle = workbook.createCellStyle();
			titleStyle.setFont(titleFont);
			titleStyle.setAlignment(HorizontalAlignment.CENTER);
			titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			CellStyle filterStyle = workbook.createCellStyle();
			filterStyle.setWrapText(true);
			filterStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFont(boldFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);
			headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);

			CellStyle borderedStyle = workbook.createCellStyle();
			borderedStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			borderedStyle.setBorderTop(BorderStyle.THIN);
			borderedStyle.setBorderBottom(BorderStyle.THIN);
			borderedStyle.setBorderLeft(BorderStyle.THIN);
			borderedStyle.setBorderRight(BorderStyle.THIN);

			CellStyle wrapStyle = workbook.createCellStyle();
			wrapStyle.cloneStyleFrom(borderedStyle);
			wrapStyle.setWrapText(true);
			wrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			String[] headers = { "대리점명", "신청자", "신청일", "배송희망일", "우편번호", "도", "시", "구", "도로명주소", "상세주소", "수량", "제품비용",
					"주문메모", "팀카테고리", "배송수단", "배송담당자", "옵션 정보" };

			Row titleRow = sheet.createRow(0);
			titleRow.setHeightInPoints(24);
			Cell titleCell = titleRow.createCell(0);
			titleCell.setCellValue("관리자 발주관리 조회 결과");
			titleCell.setCellStyle(titleStyle);
			sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

			Row filterRow = sheet.createRow(1);
			filterRow.setHeightInPoints(36);
			Cell filterCell = filterRow.createCell(0);
			filterCell.setCellValue(filters.stream()
					.map(filter -> filter.label() + ": " + filter.value())
					.collect(Collectors.joining(" | ")));
			filterCell.setCellStyle(filterStyle);
			sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));

			Row countRow = sheet.createRow(2);
			Cell countCell = countRow.createCell(0);
			countCell.setCellValue("조회 건수: " + orderList.size() + "건");
			countCell.setCellStyle(filterStyle);
			sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, headers.length - 1));

			Row header = sheet.createRow(3);
			header.setHeightInPoints(22);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, i == 16 ? 12000 : (i == 8 || i == 9 || i == 12 ? 9000 : 5000));
			}
			sheet.createFreezePane(0, 4);

			int rowIdx = 4;

			for (Order order : orderList) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				OrderItem item = order.getOrderItem();

				String agencyName = safe(() -> order.getTask().getRequestedBy().getCompany().getCompanyName(), "미지정");
				String requester = safe(() -> order.getTask().getRequestedBy().getName(), "미지정");
				String createdAt = order.getCreatedAt() != null ? order.getCreatedAt().toString() : "";
				String deliveryDate = order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toString() : "";
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

				Cell optionCell = row.createCell(16);
				optionCell.setCellValue(buildOptionsTextNoTrailing(item));
				optionCell.setCellStyle(wrapStyle);

				for (int i = 0; i <= 15; i++) {
					Cell cell = row.getCell(i);
					if (cell != null) {
						cell.setCellStyle(i == 8 || i == 9 || i == 12 ? wrapStyle : borderedStyle);
					}
				}
			}

			workbook.write(response.getOutputStream());
		}
	}


	private String normalizeNullableSearchText(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private List<NonStandardTaskListFilterItem> buildNonStandardTaskListFilterItems(
			String keyword,
			Long orderIdFrom,
			Long orderIdTo,
			String productName,
			String dateCriteria,
			DateRange range,
			Long productCategoryId,
			OrderStatus status,
			Boolean standard
	) {
		String categoryLabel = productCategoryId == null
				? "전체"
				: teamCategoryRepository.findById(productCategoryId)
						.map(TeamCategory::getName)
						.filter(name -> name != null && !name.isBlank())
						.orElse("ID " + productCategoryId);

		String dateLabel;
		if ("order".equals(dateCriteria)) {
			dateLabel = "발주일 " + buildFilterDateRangeText(range);
		} else if ("delivery".equals(dateCriteria)) {
			dateLabel = "출고일 " + buildFilterDateRangeText(range);
		} else {
			dateLabel = "전체기간";
		}

		return List.of(
				new NonStandardTaskListFilterItem("오더 ID", buildOrderIdRangeText(orderIdFrom, orderIdTo)),
				new NonStandardTaskListFilterItem("제품명", productName != null ? productName : "전체"),
				new NonStandardTaskListFilterItem("키워드", keyword != null ? keyword : "전체"),
				new NonStandardTaskListFilterItem("기간", dateLabel),
				new NonStandardTaskListFilterItem("제품분류", categoryLabel),
				new NonStandardTaskListFilterItem("발주상태", status != null ? status.getLabel() : "전체"),
				new NonStandardTaskListFilterItem("규격 여부",
						standard == null ? "전체" : (standard ? "규격" : "비규격"))
		);
	}

	private String buildFilterDateRangeText(DateRange range) {
		if (range == null) {
			return "전체";
		}
		String start = normalizeNullableSearchText(range.getStartDateStr());
		String end = normalizeNullableSearchText(range.getEndDateStr());
		if (start == null && end == null) {
			return "전체";
		}
		return (start != null ? start : "처음") + " ~ " + (end != null ? end : "현재");
	}

	private List<Order> sortNonStandardTaskListOutputOrders(
			List<Order> orders,
			List<NonStandardTaskListSortCriterion> criteria
	) {
		if (orders == null || orders.isEmpty()) {
			return List.of();
		}

		Comparator<Order> comparator = null;
		if (criteria != null) {
			for (NonStandardTaskListSortCriterion criterion : criteria) {
				Comparator<Order> next = buildNonStandardTaskListOutputComparator(criterion);
				if (next != null) {
					comparator = comparator == null ? next : comparator.thenComparing(next);
				}
			}
		}

		if (comparator == null) {
			comparator = Comparator.comparing(
					Order::getCreatedAt,
					Comparator.nullsLast(Comparator.reverseOrder())
			);
		}

		comparator = comparator.thenComparing(
				Order::getId,
				Comparator.nullsLast(Comparator.reverseOrder())
		);

		return orders.stream().sorted(comparator).toList();
	}

	private Comparator<Order> buildNonStandardTaskListOutputComparator(
			NonStandardTaskListSortCriterion criterion
	) {
		if (criterion == null || criterion.field() == null) {
			return null;
		}

		boolean ascending = "asc".equals(criterion.direction());

		return switch (criterion.field()) {
		case "orderId" -> Comparator.comparing(Order::getId, nullableComparableComparator(ascending));
		case "agencyName" -> Comparator.comparing(this::resolveNonStandardTaskAgencyName,
				nullableComparableComparator(ascending));
		case "productCategoryName" -> Comparator.comparing(this::resolveNonStandardTaskCategoryName,
				nullableComparableComparator(ascending));
		case "standard" -> Comparator.comparing(Order::isStandard,
				nullableComparableComparator(ascending));
		case "orderDate" -> Comparator.comparing(Order::getCreatedAt,
				nullableComparableComparator(ascending));
		case "preferredDeliveryDate" -> Comparator.comparing(Order::getPreferredDeliveryDate,
				nullableComparableComparator(ascending));
		case "status" -> Comparator.comparing(
				order -> order != null && order.getStatus() != null ? order.getStatus().name() : null,
				nullableComparableComparator(ascending)
		);
		default -> null;
		};
	}

	private <T extends Comparable<? super T>> Comparator<T> nullableComparableComparator(boolean ascending) {
		Comparator<T> valueComparator = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
		return Comparator.nullsLast(valueComparator);
	}

	private String resolveNonStandardTaskAgencyName(Order order) {
		if (order == null || order.getTask() == null || order.getTask().getRequestedBy() == null
				|| order.getTask().getRequestedBy().getCompany() == null) {
			return null;
		}
		return normalizeNullableSearchText(order.getTask().getRequestedBy().getCompany().getCompanyName());
	}

	private String resolveNonStandardTaskCategoryName(Order order) {
		return order != null && order.getProductCategory() != null
				? normalizeNullableSearchText(order.getProductCategory().getName())
				: null;
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

	private record OrderIdRangeFilter(Long from, Long to) {
	}

	/**
	 * 오더 ID 검색 범위를 정규화합니다.
	 * 기존 orderId 파라미터만 전달된 URL은 단건 범위(FROM=TO)로 호환합니다.
	 */
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

	private String buildOrderIdRangeText(Long orderIdFrom, Long orderIdTo) {
		if (orderIdFrom == null && orderIdTo == null) {
			return "전체";
		}

		if (orderIdFrom != null && orderIdTo != null) {
			return orderIdFrom.equals(orderIdTo)
					? String.valueOf(orderIdFrom)
					: orderIdFrom + " ~ " + orderIdTo;
		}

		return orderIdFrom != null
				? orderIdFrom + " 이상"
				: orderIdTo + " 이하";
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

		// 화면 자동완성용 DTO
		List<NonStandardOrderCompanyOptionDto> companyOptions = companies.stream().map(company -> {
			String representativeName = memberRepository
					.findCompanyMembersByRole(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE, PageRequest.of(0, 1))
					.stream().findFirst().map(Member::getName).orElse("");

			return new NonStandardOrderCompanyOptionDto(company.getId(), company.getCompanyName(), representativeName);
		}).toList();

		// 현재 선택된 회사의 멤버 목록(기존 hidden requesterMemberId 보정용)
		List<Member> companyMembers = (selectedCompanyId != null) ? memberRepository.findByCompany_Id(selectedCompanyId)
				: List.of();

		model.addAttribute("companyOptions", companyOptions);
		model.addAttribute("companyMembers", companyMembers);
		model.addAttribute("selectedCompanyId", selectedCompanyId);
		model.addAttribute("selectedMemberId", selectedMemberId);

		return "administration/management/order/nonStandard/orderItemDetail";
	}

	@PostMapping("/nonStandardOrderItemUpdate/{orderId}")
	public String updateNonStandardOrderItem(@PathVariable Long orderId,
			@RequestParam(value = "productCost", defaultValue = "0") int productCost,
			@RequestParam(value = "quantity", defaultValue = "0") int quantity,
			@RequestParam(value = "supplyPrice", defaultValue = "0") int supplyPrice,
			@RequestParam(value = "totalAmount", defaultValue = "0") int totalAmount,
			@RequestParam(value = "packingCost", defaultValue = "0") int packingCost,
			@RequestParam(value = "deliveryCost", defaultValue = "0") int deliveryCost,
			@RequestParam(value = "mirrorCuttingProduct", defaultValue = "false") boolean mirrorCuttingProduct,
			@RequestParam(value = "preferredDeliveryDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDeliveryDate,
			@RequestParam("status") String statusStr, @RequestParam("deliveryMethodId") Optional<Long> deliveryMethodId,
			@RequestParam("assignedDeliveryHandlerId") Optional<Long> deliveryHandlerId,
			@RequestParam("productCategoryId") Optional<Long> productCategoryId,
			@RequestParam("companyId") Optional<Long> companyId,
			@RequestParam("requesterMemberId") Optional<Long> requesterMemberId,
			@RequestParam(value = "zipCode", required = false) String zipCode,
			@RequestParam(value = "doName", required = false) String doName,
			@RequestParam(value = "siName", required = false) String siName,
			@RequestParam(value = "guName", required = false) String guName,
			@RequestParam(value = "roadAddress", required = false) String roadAddress,
			@RequestParam(value = "detailAddress", required = false) String detailAddress,
			@RequestParam(value = "siteZipCode", required = false) String siteZipCode,
			@RequestParam(value = "siteDoName", required = false) String siteDoName,
			@RequestParam(value = "siteSiName", required = false) String siteSiName,
			@RequestParam(value = "siteGuName", required = false) String siteGuName,
			@RequestParam(value = "siteRoadAddress", required = false) String siteRoadAddress,
			@RequestParam(value = "siteDetailAddress", required = false) String siteDetailAddress,
			@RequestParam(value = "ordererName", required = false) String ordererName,
			@RequestParam(value = "ordererPhone", required = false) String ordererPhone,
			@RequestParam(value = "optionJson", required = false) String optionJson,
			@RequestParam(value = "adminMemo", required = false) String adminMemo,
			@RequestParam(value = "dispatchCompleteMessage", required = false) String dispatchCompleteMessage,
			@RequestParam(value = "dispatchCompleteMessageSubmitted", defaultValue = "false") boolean dispatchCompleteMessageSubmitted,
			@RequestParam(value = "deleteAdminImageIds", required = false) List<Long> deleteAdminImageIds,
			@RequestParam(value = "adminImages", required = false) List<MultipartFile> adminImages,
			@RequestParam(value = "returnUrl", required = false) String returnUrl, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		String redirectUrl = isSafeNonStandardTaskListReturnUrl(returnUrl) ? "redirect:" + returnUrl.trim()
				: "redirect:/management/nonStandardTaskList";

		try {
			String updatedByUsername = resolveAuthenticatedUsername(authentication);

			nonStandardOrderItemService.updateNonStandardOrderItemWithSiteAddress(orderId, productCost, quantity,
					supplyPrice, totalAmount, packingCost, deliveryCost, mirrorCuttingProduct, preferredDeliveryDate,
					statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId, companyId, requesterMemberId, zipCode,
					doName, siName, guName, roadAddress, detailAddress, siteZipCode, siteDoName, siteSiName, siteGuName,
					siteRoadAddress, siteDetailAddress, ordererName, ordererPhone, optionJson, adminMemo,
					dispatchCompleteMessage, dispatchCompleteMessageSubmitted, deleteAdminImageIds, adminImages,
					updatedByUsername);

			redirectAttributes.addFlashAttribute("message", "주문 정보가 수정되었습니다.");
			return redirectUrl;

		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("message", e.getMessage());
			return redirectUrl;
		}
	}

	private String resolveAuthenticatedUsername(Authentication authentication) {
		if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
			return "UNKNOWN";
		}

		return authentication.getName().trim();
	}

	private boolean isSafeNonStandardTaskListReturnUrl(String returnUrl) {
		if (returnUrl == null || returnUrl.isBlank()) {
			return false;
		}

		String trimmedReturnUrl = returnUrl.trim();

		return trimmedReturnUrl.startsWith("/management/nonStandardTaskList") && !trimmedReturnUrl.startsWith("//")
				&& !trimmedReturnUrl.contains("://") && !trimmedReturnUrl.contains("\r")
				&& !trimmedReturnUrl.contains("\n");
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

	@GetMapping("/asDetail/{id}")
	public String asDetail(@PathVariable Long id, Model model) {
		AsTask asTask = asTaskService.getAsDetail(id);

		model.addAttribute("asTask", asTask);
		model.addAttribute("asStatuses", AsStatus.values());
		model.addAttribute("asTeamMembers", memberRepository.findByTeamName("AS팀"));
		model.addAttribute("billingTargets", AsBillingTarget.values());

		return "administration/management/as/asDetail";
	}

	// =========================================================
	// AS LIST / EXCEL 전용 상수
	// - 기존 다른 화면 유틸과 충돌하지 않도록 전부 AS 전용 이름 사용
	// =========================================================
	private static final Set<String> ALLOWED_AS_LIST_KEYWORD_TYPES = Set.of("all", "companyName", "requesterName",
			"customerName", "subject", "productName", "applicantName", "applicantPhone", "onsiteContact");

	private static final Set<String> ALLOWED_AS_LIST_SORT_FIELDS = Set.of("companyName", "requesterName", "handlerName",
			"requestedAt", "scheduledDate", "asProcessDate", "status");

	@GetMapping("/asList")
	public String asList(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) Long handlerId, @RequestParam(required = false) AsStatus status,
			@RequestParam(required = false) String dateType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

			@RequestParam(required = false) String priceFilter,
			@RequestParam(required = false) String paymentCollectedFilter,

			@RequestParam(required = false) String keywordType, @RequestParam(required = false) String keyword,

			@RequestParam(required = false) String sortField, @RequestParam(required = false) String sortDir,

			Pageable pageable, Model model) {
		String resolvedDateType = (dateType == null || dateType.isBlank()) ? "requested" : dateType;

		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		String resolvedPriceFilter = normalizeAsListPriceFilter(priceFilter);
		Boolean resolvedPaymentCollected = normalizeAsListPaymentCollectedFilter(paymentCollectedFilter);
		String selectedPaymentCollectedFilter = resolvedPaymentCollected == null ? ""
				: (resolvedPaymentCollected ? "Y" : "N");

		String resolvedKeywordType = normalizeAsListKeywordType(keywordType);
		String resolvedKeyword = normalizeAsListKeyword(keyword);

		String resolvedSortField = normalizeAsListSortField(sortField);
		String resolvedSortDir = normalizeAsListSortDir(sortDir);

		Pageable resolvedPageable = resolveAsListPageable(pageable);

		Page<AsTask> asPage = asTaskService.getFilteredAsListPage(handlerId, status, resolvedDateType, start, end,
				resolvedPriceFilter, resolvedPaymentCollected, resolvedKeywordType, resolvedKeyword, resolvedSortField,
				resolvedSortDir, resolvedPageable);

		Map<Long, LocalDate> scheduledDateMap = asTaskService.getScheduledDateMap(asPage.getContent());

		model.addAttribute("asPage", asPage);
		model.addAttribute("scheduledDateMap", scheduledDateMap);

		model.addAttribute("asHandlers", memberRepository.findByTeamName("AS팀"));
		model.addAttribute("selectedHandlerId", handlerId);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedDateType", resolvedDateType);
		model.addAttribute("selectedFromDate", fromDate);
		model.addAttribute("selectedToDate", toDate);
		model.addAttribute("selectedPriceFilter", resolvedPriceFilter == null ? "" : resolvedPriceFilter);
		model.addAttribute("selectedPaymentCollectedFilter", selectedPaymentCollectedFilter);
		model.addAttribute("selectedKeywordType", resolvedKeywordType);
		model.addAttribute("selectedKeyword", resolvedKeyword == null ? "" : resolvedKeyword);
		model.addAttribute("sortField", resolvedSortField == null ? "" : resolvedSortField);
		model.addAttribute("sortDir", resolvedSortDir == null ? "" : resolvedSortDir);
		model.addAttribute("pageSize", resolvedPageable.getPageSize());

		return "administration/management/as/asList";
	}

	@GetMapping("/asList/excel")
	public void downloadAsListExcel(@RequestParam(required = false) Long handlerId,
			@RequestParam(required = false) AsStatus status, @RequestParam(required = false) String dateType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

			@RequestParam(required = false) String priceFilter,
			@RequestParam(required = false) String paymentCollectedFilter,

			@RequestParam(required = false) String keywordType, @RequestParam(required = false) String keyword,

			@RequestParam(required = false) String sortField, @RequestParam(required = false) String sortDir,

			HttpServletResponse response) throws IOException {

		String resolvedDateType = (dateType == null || dateType.isBlank()) ? "requested" : dateType;

		LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
		LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

		String resolvedPriceFilter = normalizeAsListPriceFilter(priceFilter);
		Boolean resolvedPaymentCollected = normalizeAsListPaymentCollectedFilter(paymentCollectedFilter);
		String resolvedKeywordType = normalizeAsListKeywordType(keywordType);
		String resolvedKeyword = normalizeAsListKeyword(keyword);
		String resolvedSortField = normalizeAsListSortField(sortField);
		String resolvedSortDir = normalizeAsListSortDir(sortDir);

		List<AsTask> asTasks = asTaskService.getFilteredAsListAll(handlerId, status, resolvedDateType, start, end,
				resolvedPriceFilter, resolvedPaymentCollected, resolvedKeywordType, resolvedKeyword, resolvedSortField,
				resolvedSortDir);

		Map<Long, LocalDate> scheduledDateMap = asTaskService.getScheduledDateMap(asTasks);

		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=as_task_list.xlsx");

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("AS 목록");

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

			Row header = sheet.createRow(0);

			String[] titles = { "대리점명", "요청자", "고객성함", "제목", "제품명", "신청인", "신청인연락처", "현장연락처", "요청일", "방문예정일", "처리일",
					"상태", "배정팀", "담당자", "주소", "요청사유", "금액", "수납상태", "비고" };

			for (int i = 0; i < titles.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(titles[i]);
				cell.setCellStyle(headerStyle);

				if (i == 3 || i == 4 || i == 14 || i == 15 || i == 18) {
					sheet.setColumnWidth(i, 10000);
				} else if (i == 6 || i == 7 || i == 9) {
					sheet.setColumnWidth(i, 6000);
				} else {
					sheet.setColumnWidth(i, 5000);
				}
			}

			int rowIdx = 1;
			for (AsTask task : asTasks) {
				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(60);

				String companyName = "-";
				if (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null) {
					companyName = safeAsListExcelText(task.getRequestedBy().getCompany().getCompanyName());
				}

				String requesterName = (task.getRequestedBy() != null)
						? safeAsListExcelText(task.getRequestedBy().getName())
						: "-";

				String customerName = task.getCustomerNameSafe();
				String subject = task.getSubjectSafe();
				String productName = task.getProductNameSafe();
				String applicantName = task.getApplicantNameSafe();
				String applicantPhone = task.getApplicantPhoneSafe();
				String onsiteContact = task.getOnsiteContactSafe();

				String requestedAt = (task.getRequestedAt() != null) ? task.getRequestedAt().format(dateTimeFormatter)
						: "";

				LocalDate scheduledDateValue = scheduledDateMap.get(task.getId());
				String scheduledDate = (scheduledDateValue != null) ? scheduledDateValue.format(dateFormatter) : "";

				String processedAt = (task.getAsProcessDate() != null)
						? task.getAsProcessDate().format(dateTimeFormatter)
						: "";

				String statusText = (task.getStatus() != null) ? task.getStatus().name() : "";
				String assignedTeam = (task.getAssignedTeam() != null)
						? safeAsListExcelText(task.getAssignedTeam().getName())
						: "";
				String handlerName = (task.getAssignedHandler() != null)
						? safeAsListExcelText(task.getAssignedHandler().getName())
						: "";
				String address = (safeAsListExcelText(task.getRoadAddress()) + " "
						+ safeAsListExcelText(task.getDetailAddress())).trim();
				String reason = safeAsListExcelText(task.getReason());
				String price = (task.getPrice() > 0) ? String.format("%,d", task.getPrice()) : "0";
				String paymentCollected = task.isPaymentCollected() ? "수납완료" : "미수납";
				String comment = safeAsListExcelText(task.getAsComment());

				createAsListExcelCell(row, 0, companyName, borderedStyle);
				createAsListExcelCell(row, 1, requesterName, borderedStyle);
				createAsListExcelCell(row, 2, customerName, borderedStyle);
				createAsListExcelCell(row, 3, subject, wrapStyle);
				createAsListExcelCell(row, 4, productName, wrapStyle);
				createAsListExcelCell(row, 5, applicantName, borderedStyle);
				createAsListExcelCell(row, 6, applicantPhone, borderedStyle);
				createAsListExcelCell(row, 7, onsiteContact, borderedStyle);
				createAsListExcelCell(row, 8, requestedAt, borderedStyle);
				createAsListExcelCell(row, 9, scheduledDate, borderedStyle);
				createAsListExcelCell(row, 10, processedAt, borderedStyle);
				createAsListExcelCell(row, 11, statusText, borderedStyle);
				createAsListExcelCell(row, 12, assignedTeam, borderedStyle);
				createAsListExcelCell(row, 13, handlerName, borderedStyle);
				createAsListExcelCell(row, 14, address, wrapStyle);
				createAsListExcelCell(row, 15, reason, wrapStyle);
				createAsListExcelCell(row, 16, price, borderedStyle);
				createAsListExcelCell(row, 17, paymentCollected, borderedStyle);
				createAsListExcelCell(row, 18, comment, wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
	}

	// =========================================================
	// AS LIST / EXCEL 전용 유틸
	// - 기존 normalizeSortField / normalizeSortDir / safe / createCell 과 충돌 금지
	// =========================================================
	private String normalizeAsListKeyword(String keyword) {
		if (keyword == null) {
			return null;
		}

		String normalized = keyword.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String normalizeAsListKeywordType(String keywordType) {
		if (keywordType == null || keywordType.isBlank()) {
			return "all";
		}

		String normalized = keywordType.trim();
		return ALLOWED_AS_LIST_KEYWORD_TYPES.contains(normalized) ? normalized : "all";
	}

	private String normalizeAsListPriceFilter(String priceFilter) {
		if (priceFilter == null || priceFilter.isBlank()) {
			return null;
		}

		String normalized = priceFilter.trim().toUpperCase(Locale.ROOT);

		if ("ZERO".equals(normalized)) {
			return "ZERO";
		}
		if ("POSITIVE".equals(normalized)) {
			return "POSITIVE";
		}

		return null;
	}

	private Boolean normalizeAsListPaymentCollectedFilter(String paymentCollectedFilter) {
		if (paymentCollectedFilter == null || paymentCollectedFilter.isBlank()) {
			return null;
		}

		String normalized = paymentCollectedFilter.trim().toUpperCase(Locale.ROOT);

		if ("Y".equals(normalized)) {
			return Boolean.TRUE;
		}
		if ("N".equals(normalized)) {
			return Boolean.FALSE;
		}

		return null;
	}

	private String normalizeAsListSortField(String sortField) {
		if (sortField == null || sortField.isBlank()) {
			return null;
		}

		String normalized = sortField.trim();
		return ALLOWED_AS_LIST_SORT_FIELDS.contains(normalized) ? normalized : null;
	}

	private String normalizeAsListSortDir(String sortDir) {
		if (sortDir == null || sortDir.isBlank()) {
			return null;
		}

		String normalized = sortDir.trim().toLowerCase(Locale.ROOT);
		if ("asc".equals(normalized) || "desc".equals(normalized)) {
			return normalized;
		}
		return null;
	}

	private Pageable resolveAsListPageable(Pageable pageable) {
		int page = Math.max(pageable.getPageNumber(), 0);
		int size = pageable.getPageSize() > 0 ? pageable.getPageSize() : 10;
		return PageRequest.of(page, size);
	}

	private void createAsListExcelCell(Row row, int col, String value, CellStyle style) {
		Cell c = row.createCell(col);
		c.setCellValue(value != null ? value : "");
		c.setCellStyle(style);
	}

	private String safeAsListExcelText(String value) {
		return (value == null) ? "" : value;
	}

	// =========================================================
	// 1) 회사 검색 API (AJAX)
	// =========================================================
	@GetMapping("/api/companies/search")
	@ResponseBody
	public List<CompanySearchItemDto> searchCompanies(@RequestParam("keyword") String keyword,
			@RequestParam(value = "limit", defaultValue = "30") int limit) {
		return asTaskService.searchCompaniesForAs(keyword, limit);
	}

	// =========================================================
	// 2) AS 업데이트 (multipart) - 한 번에 저장
	// =========================================================
	@PostMapping(value = "/asUpdate/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public String updateAsTask(@PathVariable Long id,

			@RequestParam(required = false) String price, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long assignedHandlerId,

			@RequestParam(required = false) Long companyId,

			@RequestParam(required = false) String zipCode, @RequestParam(required = false) String doName,
			@RequestParam(required = false) String siName, @RequestParam(required = false) String guName,
			@RequestParam(required = false) String roadAddress, @RequestParam(required = false) String detailAddress,

			@RequestParam(required = false) String customerName, @RequestParam(required = false) String productName,
			@RequestParam(required = false) String productSize, @RequestParam(required = false) String productColor,
			@RequestParam(required = false) String productOptions, @RequestParam(required = false) String onsiteContact,

			@RequestParam(required = false) String applicantName, @RequestParam(required = false) String applicantPhone,
			@RequestParam(required = false) String applicantEmail, @RequestParam(required = false) String purchaseDate,
			@RequestParam(required = false) String billingTarget,

			@RequestParam(required = false, defaultValue = "false") Boolean paymentCollected,

			@RequestParam(required = false) String subject, @RequestParam(required = false) String adminMemo,

			@RequestParam(required = false) String deleteRequestImageIds,
			@RequestParam(value = "newRequestImages", required = false) List<MultipartFile> newRequestImages,

			@RequestParam(required = false) String deleteRequestVideoIds,
			@RequestParam(value = "newRequestVideos", required = false) List<MultipartFile> newRequestVideos) {

		asTaskService.updateAsTaskThird(id, price, status, assignedHandlerId,

				companyId,

				zipCode, doName, siName, guName, roadAddress, detailAddress,

				customerName, productName, productSize, productColor, productOptions, onsiteContact,

				applicantName, applicantPhone, applicantEmail, purchaseDate, billingTarget, paymentCollected,

				subject, adminMemo,

				deleteRequestImageIds, newRequestImages, deleteRequestVideoIds, newRequestVideos);

		return "redirect:/management/asDetail/" + id;
	}

	// =========================================================
	// 3) AS 삭제 (일정 + 이미지 + task)
	// =========================================================
	@PostMapping("/asDelete/{id}")
	public String deleteAsTask(@PathVariable Long id) {
		asTaskService.deleteAsTaskCascade(id);
		return "redirect:/management/asList";
	}

	private String safe(String v) {
		return (v == null) ? "" : v;
	}

	// =========================
	// 1) 리스트 페이지
	// =========================
	@GetMapping("/productionList")
	public String productionListPage(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate, @RequestParam(required = false) String dateType,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir, Pageable pageable, Model model) {
		// 1) 카테고리
		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

		// 2) dateType 기본값: created
		String finalDateType = normalizeDateType(dateType); // created/preferred

		// 3) 날짜 파싱: "없음"이면 null로 두고, 전체기간 처리
		LocalDate startD = parseLocalDateOrNull(startDate);
		LocalDate endD = parseLocalDateOrNull(endDate);

		LocalDateTime start = null;
		LocalDateTime end = null;

		if (startD != null && endD != null && endD.isBefore(startD)) {
			LocalDate tmp = startD;
			startD = endD;
			endD = tmp;
		}
		if (startD != null)
			start = startD.atStartOfDay();
		if (endD != null)
			end = endD.atTime(LocalTime.MAX);

		// 4) status: 기본은 전체(null). 빈값도 전체.
		OrderStatus parsedStatus = parseOrderStatusOrNull(status);
		String finalStatusParam = (status == null) ? "" : status.trim(); // 링크 유지용
		if (finalStatusParam.isBlank())
			finalStatusParam = "";

		// 5) 페이지 사이즈 적용(10/30/50/100)
		int pageSize = normalizePageSize(size, pageable.getPageSize());

		// 6) 정렬 구성
		Sort sort = buildSort(sortField, sortDir); // 기본 createdAt desc
		String finalSortField = normalizeSortField(sortField);
		String finalSortDir = normalizeSortDir(sortDir);

		Pageable finalPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);

		// 7) 조회 (AND 조건: category + status + (optional date range) + dateType)
		Page<Order> orders = orderStatusService.getOrders(start, end, category, parsedStatus, finalDateType,
				finalPageable);

		// 8) 페이지네이션 숫자 범위: 현재 페이지 기준 최대 5개만 노출
		int totalPages = orders.getTotalPages();
		int currentPage = orders.getNumber(); // 0-based
		int pageWindowSize = 5;

		int pageStart = 0;
		int pageEnd = -1;
		List<Integer> pageNumbers = List.of();

		if (totalPages > 0) {
			pageStart = Math.max(0, currentPage - (pageWindowSize / 2));
			pageEnd = Math.min(totalPages - 1, pageStart + pageWindowSize - 1);
			pageStart = Math.max(0, pageEnd - pageWindowSize + 1);

			pageNumbers = IntStream.rangeClosed(pageStart, pageEnd)
					.boxed()
					.toList();
		}
		
		// 8) View Model
		model.addAttribute("orders", orders);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("status", finalStatusParam);

		model.addAttribute("startDateStr", startD != null ? startD.format(DateTimeFormatter.ISO_DATE) : "");
		model.addAttribute("endDateStr", endD != null ? endD.format(DateTimeFormatter.ISO_DATE) : "");
		model.addAttribute("dateType", finalDateType);

		model.addAttribute("pageSize", pageSize);
		model.addAttribute("sortField", finalSortField);
		model.addAttribute("sortDir", finalSortDir);
		
		model.addAttribute("pageStart", pageStart);
		model.addAttribute("pageEnd", pageEnd);
		model.addAttribute("pageNumbers", pageNumbers);
		
		model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/production/productionList";
	}

	// =========================
	// Helpers
	// =========================

	private static String normalizeDateType(String dateType) {
		if (dateType == null || dateType.isBlank())
			return "created";
		String v = dateType.trim().toLowerCase();
		return ("preferred".equals(v) ? "preferred" : "created");
	}

	private static LocalDate parseLocalDateOrNull(String v) {
		if (v == null)
			return null;
		String s = v.trim();
		if (s.isEmpty())
			return null;
		try {
			return LocalDate.parse(s, DateTimeFormatter.ISO_DATE);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static OrderStatus parseOrderStatusOrNull(String status) {
		if (status == null)
			return null;
		String s = status.trim();
		if (s.isEmpty())
			return null;
		try {
			return OrderStatus.valueOf(s);
		} catch (IllegalArgumentException e) {
			return null; // 이상값은 전체로 처리(안전)
		}
	}

	private static int normalizePageSize(Integer size, int fallback) {
		int v = (size == null ? fallback : size);
		if (v == 10 || v == 30 || v == 50 || v == 100)
			return v;
		// 기본 10
		return 10;
	}

	private static String normalizeSortField(String sortField) {
		if (sortField == null)
			return "";
		String f = sortField.trim();
		return switch (f) {
		case "companyName", "requesterName", "standard", "createdAt", "preferredDeliveryDate", "status" -> f;
		default -> "";
		};
	}

	private static String normalizeSortDir(String sortDir) {
		if (sortDir == null)
			return "desc";
		String d = sortDir.trim().toLowerCase();
		return ("asc".equals(d) ? "asc" : "desc");
	}

	private static Sort buildSort(String sortField, String sortDir) {
		String f = normalizeSortField(sortField);
		String d = normalizeSortDir(sortDir);

		// 기본 정렬
		if (f.isBlank()) {
			return Sort.by(Sort.Direction.DESC, "createdAt");
		}

		Sort.Direction dir = "asc".equals(d) ? Sort.Direction.ASC : Sort.Direction.DESC;

		// 정렬 매핑 (주의: 연관필드 정렬은 JPQL join을 태워야 해서 아래처럼 "조인 경로"로 맞춥니다)
		// repository 쿼리에서 join을 걸어둔 상태여야 정상 동작합니다(아래 Repository에서 해결).
		return switch (f) {
		case "companyName" -> Sort.by(dir, "task.requestedBy.company.companyName");
		case "requesterName" -> Sort.by(dir, "task.requestedBy.name");
		case "standard" -> Sort.by(dir, "standard");
		case "createdAt" -> Sort.by(dir, "createdAt");
		case "preferredDeliveryDate" -> Sort.by(dir, "preferredDeliveryDate");
		case "status" -> Sort.by(dir, "status");
		default -> Sort.by(Sort.Direction.DESC, "createdAt");
		};
	}

	@GetMapping("/productionList/excel")
	public void downloadProductionListExcel(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate, @RequestParam(required = false) String dateType,
			@RequestParam(required = false) String sortField, @RequestParam(required = false) String sortDir,
			HttpServletResponse response) throws IOException {

		// 1) 카테고리
		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;

		// 2) dateType 기본값 created
		String finalDateType = normalizeDateType(dateType);

		// 3) 날짜 파싱: 없으면 null(=전체기간)
		LocalDate startD = parseLocalDateOrNull(startDate);
		LocalDate endD = parseLocalDateOrNull(endDate);

		LocalDateTime start = null;
		LocalDateTime end = null;

		if (startD != null && endD != null && endD.isBefore(startD)) {
			LocalDate tmp = startD;
			startD = endD;
			endD = tmp;
		}
		if (startD != null)
			start = startD.atStartOfDay();
		if (endD != null)
			end = endD.atTime(LocalTime.MAX);

		// 4) status: 전체(null)
		OrderStatus parsedStatus = parseOrderStatusOrNull(status);

		// 5) 정렬: 화면과 동일하게 적용(원하시면 여기만 createdAt desc 고정으로 변경 가능)
		Sort sort = buildSort(sortField, sortDir);

		// 6) 전체 조회
		List<Order> orders = orderStatusService.getAllOrders(start, end, category, parsedStatus, finalDateType, sort);

		// 7) 응답 헤더
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=production_task_orders.xlsx");

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Production Orders");

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

			CellStyle labelStyle = workbook.createCellStyle();
			labelStyle.cloneStyleFrom(headerStyle);
			labelStyle.setWrapText(true);

			// 컬럼 폭
			// 0:규격/비규격, 1:주소, 2:수량, 3:가격, 4:신청일, 5:배송희망일, 6:배송수단, 7:배송담당자, 8:상세
			sheet.setColumnWidth(0, 3500);
			sheet.setColumnWidth(1, 14000);
			sheet.setColumnWidth(2, 4000);
			sheet.setColumnWidth(3, 6000);
			sheet.setColumnWidth(4, 8000);
			sheet.setColumnWidth(5, 8000);
			sheet.setColumnWidth(6, 7000);
			sheet.setColumnWidth(7, 7000);
			sheet.setColumnWidth(8, 24000);

			int rowIdx = 0;
			Long lastTaskId = null;
			boolean freezeApplied = false;

			for (Order order : orders) {
				Task task = order.getTask();

				// Task 블록 시작
				if (task != null && (lastTaskId == null || !task.getId().equals(lastTaskId))) {

					Row labelRow = sheet.createRow(rowIdx++);
					labelRow.setHeightInPoints(28);

					Cell labelCell = labelRow.createCell(0);

					String companyName = (task.getRequestedBy() != null && task.getRequestedBy().getCompany() != null)
							? safe(task.getRequestedBy().getCompany().getCompanyName())
							: "";

					String requesterName = (task.getRequestedBy() != null) ? safe(task.getRequestedBy().getName()) : "";
					String taskCreatedAt = (task.getCreatedAt() != null) ? task.getCreatedAt().format(dtf) : "";

					labelCell.setCellValue(
							"[대리점명] " + companyName + " / [요청자명] " + requesterName + " / [발주일] " + taskCreatedAt);
					labelCell.setCellStyle(labelStyle);

					sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 8));

					Row header = sheet.createRow(rowIdx++);
					header.setHeightInPoints(20);

					String[] titles = { "규격/비규격", "배송지 주소", "수량", "가격", "신청일", "배송희망일", "배송수단", "배송담당자", "상세사항" };
					for (int i = 0; i < titles.length; i++) {
						Cell cell = header.createCell(i);
						cell.setCellValue(titles[i]);
						cell.setCellStyle(headerStyle);
					}

					if (!freezeApplied) {
						sheet.createFreezePane(0, rowIdx);
						freezeApplied = true;
					}

					lastTaskId = task.getId();
				}

				Row row = sheet.createRow(rowIdx++);
				row.setHeightInPoints(90);

				// 0) 규격/비규격
				Cell c0 = row.createCell(0);
				c0.setCellValue(order.isStandard() ? "규격" : "비규격");
				c0.setCellStyle(borderedStyle);

				// 1) 주소
				String addr = (safe(order.getRoadAddress()) + " " + safe(order.getDetailAddress())).trim();
				Cell c1 = row.createCell(1);
				c1.setCellValue(addr);
				c1.setCellStyle(wrapStyle);

				// 2) 수량
				Cell c2 = row.createCell(2);
				c2.setCellValue(order.getQuantity());
				c2.setCellStyle(borderedStyle);

				// 3) 가격
				Cell c3 = row.createCell(3);
				c3.setCellValue(order.getProductCost());
				c3.setCellStyle(moneyStyle);

				// 4) 신청일(createdAt)
				Cell c4 = row.createCell(4);
				c4.setCellValue(order.getCreatedAt() != null ? order.getCreatedAt().format(dtf) : "");
				c4.setCellStyle(borderedStyle);

				// 5) 배송희망일
				Cell c5 = row.createCell(5);
				c5.setCellValue(
						order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().format(dtf) : "");
				c5.setCellStyle(borderedStyle);

				// 6) 배송수단
				Cell c6 = row.createCell(6);
				c6.setCellValue(
						order.getDeliveryMethod() != null ? safe(order.getDeliveryMethod().getMethodName()) : "");
				c6.setCellStyle(borderedStyle);

				// 7) 배송담당자
				Cell c7 = row.createCell(7);
				c7.setCellValue(
						order.getAssignedDeliveryHandler() != null ? safe(order.getAssignedDeliveryHandler().getName())
								: "");
				c7.setCellStyle(borderedStyle);

				// 8) 상세사항(카테고리/제품명/옵션)
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

				Cell c8 = row.createCell(8);
				c8.setCellValue(detail.toString().trim());
				c8.setCellStyle(wrapStyle);
			}

			workbook.write(response.getOutputStream());
		}
	}

	// =========================
	// Helper (유틸 성격)
	// =========================

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
	public String deliveryListPage(
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assignedMemberId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long deliveryMethodId,
			@RequestParam(required = false) String dateType,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) Long orderIdFrom,
			@RequestParam(required = false) Long orderIdTo,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false) String companyName,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			@RequestParam(required = false, defaultValue = "0") Integer page,
			@RequestParam(required = false, defaultValue = "100") Integer size,
			Model model
	) {
		SearchCondition condition;

		try {
			condition = managementDeliveryListService.resolveCondition(
					categoryId,
					assignedMemberId,
					status,
					deliveryMethodId,
					dateType,
					startDate,
					endDate,
					orderIdFrom,
					orderIdTo,
					productName,
					companyName,
					sortField,
					sortDir,
					page,
					size
			);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}

		SearchResult result = managementDeliveryListService.search(condition);
		Page<GroupRow> groups = result.groups();

		int totalPages = groups.getTotalPages();
		int currentPage = groups.getNumber();
		int pageStart = 0;
		int pageEnd = -1;
		List<Integer> pageNumbers = List.of();

		if (totalPages > 0) {
			pageStart = Math.max(0, currentPage - 2);
			pageEnd = Math.min(totalPages - 1, pageStart + 4);
			pageStart = Math.max(0, pageEnd - 4);
			pageNumbers = IntStream.rangeClosed(pageStart, pageEnd).boxed().toList();
		}

		model.addAttribute("groups", groups);
		model.addAttribute("filteredOrderCount", result.filteredOrderCount());
		model.addAttribute("activeFilters", result.filters());
		model.addAttribute("pageNumbers", pageNumbers);

		model.addAttribute("categoryId", condition.categoryId());
		model.addAttribute("assignedMemberId", condition.assignedMemberId());
		model.addAttribute("status", condition.statusForView());
		model.addAttribute("deliveryMethodId", condition.deliveryMethodId());
		model.addAttribute("dateType", condition.dateType());
		model.addAttribute("startDate", condition.startDate());
		model.addAttribute("endDate", condition.endDate());
		model.addAttribute("orderIdFrom", condition.orderIdFrom());
		model.addAttribute("orderIdTo", condition.orderIdTo());
		model.addAttribute("productName", condition.productName() != null ? condition.productName() : "");
		model.addAttribute("companyName", condition.companyName() != null ? condition.companyName() : "");
		model.addAttribute("sortField", condition.sortField());
		model.addAttribute("sortDir", condition.sortDir());
		model.addAttribute("pageSize", condition.size());

		model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("assignees",
				memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAsc("배송팀"));
		model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/delivery/deliveryList";
	}

	/**
	 * 관리자 배송관리 조회결과를 화면과 동일한 묶음 기준으로 전체 출력합니다.
	 */
	@GetMapping("/deliveryList/excel")
	public void downloadDeliveryListExcel(
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long assignedMemberId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long deliveryMethodId,
			@RequestParam(required = false) String dateType,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) Long orderIdFrom,
			@RequestParam(required = false) Long orderIdTo,
			@RequestParam(required = false) String productName,
			@RequestParam(required = false) String companyName,
			@RequestParam(required = false) String sortField,
			@RequestParam(required = false) String sortDir,
			HttpServletResponse response
	) throws IOException {
		SearchCondition condition;

		try {
			condition = managementDeliveryListService.resolveCondition(
					categoryId,
					assignedMemberId,
					status,
					deliveryMethodId,
					dateType,
					startDate,
					endDate,
					orderIdFrom,
					orderIdTo,
					productName,
					companyName,
					sortField,
					sortDir,
					0,
					100
			);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}

		byte[] excelBytes;

		try {
			List<GroupRow> groups = managementDeliveryListService.findAllGroups(condition);
			excelBytes = managementDeliveryListExcelService.buildExcel(
					groups,
					managementDeliveryListService.getFilterItems(condition)
			);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}

		String filename = "관리자_배송관리_" + LocalDate.now() + ".xlsx";
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment()
						.filename(filename, StandardCharsets.UTF_8)
						.build()
						.toString()
		);
		response.setContentLength(excelBytes.length);
		response.getOutputStream().write(excelBytes);
	}

	// 관리자 배송관리 외의 기존 화면에서도 사용하는 날짜범위 유틸입니다.
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
	public ResponseEntity<ByteArrayResource> downloadClientListExcel(@RequestParam(required = false) String keyword,
			@RequestParam(required = false, defaultValue = "company") String searchType,
			@RequestParam(required = false, defaultValue = "createdAt") String sortField,
			@RequestParam(required = false, defaultValue = "desc") String sortDir,
			@RequestParam(required = false) Integer size, // 화면 상태 유지용(필수는 아님)
			@RequestParam(name = "companyIds", required = false) List<Long> companyIds) throws IOException {

		if (companyIds == null || companyIds.isEmpty()) {
			// 체크박스 미선택 방어
			return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
					.body(new ByteArrayResource("선택된 대리점이 없습니다.".getBytes()));
		}

		byte[] bytes = companyService.exportCompaniesToExcelByIds(companyIds, sortField, sortDir);

		String filename = "company_list.xlsx";

		return ResponseEntity.ok()
				.contentType(
						MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(new ByteArrayResource(bytes));
	}

	@GetMapping("/clientDetail/{id}")
	public String clientDetail(@PathVariable Long id, Model model) {
		Company company = companyRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("해당 대리점이 존재하지 않습니다. ID=" + id));

		List<Member> memberList = memberRepository.findByCompany(company);

		Member representative = memberList.stream()
				.filter(member -> member.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE)
				.min(Comparator.comparing(Member::getId)).orElse(null);

		List<CompanyDeliveryAddress> deliveryAddresses =
				companyDeliveryAddressRepository.findByCompany_IdOrderByIdAsc(id);

		model.addAttribute("company", company);
		model.addAttribute("members", memberList);
		model.addAttribute("representative", representative);
		model.addAttribute("deliveryAddressCount", deliveryAddresses.size());

		return "administration/member/client/clientDetail";
	}

	@GetMapping("/clientDetail/{companyId}/deliveryAddresses")
	@ResponseBody
	public ResponseEntity<AdminClientDeliveryAddressResponse> getClientDeliveryAddresses(
			@PathVariable Long companyId) {
		try {
			findAdminClientCompany(companyId);
			return ResponseEntity.ok(buildAdminClientDeliveryAddressResponse(
					companyId,
					"배송지 목록을 조회했습니다."
			));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(
					new AdminClientDeliveryAddressResponse(false, e.getMessage(), List.of())
			);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					new AdminClientDeliveryAddressResponse(
							false,
							"배송지 목록 조회 중 오류가 발생했습니다.",
							List.of()
					)
			);
		}
	}

	@PostMapping("/clientDetail/{companyId}/deliveryAddresses")
	@ResponseBody
	public ResponseEntity<AdminClientDeliveryAddressResponse> addClientDeliveryAddress(
			@PathVariable Long companyId,
			@RequestBody AdminClientDeliveryAddressSaveRequest request) {
		try {
			Company company = findAdminClientCompany(companyId);

			String zipCode = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.zipCode() : null, 20, true, "우편번호");
			String doName = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.doName() : null, 50, true, "도/시");
			String siName = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.siName() : null, 50, false, "시/군");
			String guName = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.guName() : null, 50, false, "구");
			String roadAddress = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.roadAddress() : null, 255, true, "기본주소");
			String detailAddress = normalizeAdminClientDeliveryAddressValue(
					request != null ? request.detailAddress() : null, 255, false, "상세주소");

			AddressRegionResolver.ResolvedRegion resolvedRegion = addressRegionResolver.resolve(
					doName,
					siName,
					guName,
					roadAddress
			);
			doName = resolvedRegion.doName();
			siName = resolvedRegion.siName();
			guName = resolvedRegion.guName();

			boolean duplicate = companyDeliveryAddressRepository.findByCompany_IdOrderByIdAsc(companyId).stream()
					.anyMatch(address -> isSameAdminClientDeliveryAddress(
							address, zipCode, roadAddress, detailAddress));

			if (duplicate) {
				throw new IllegalArgumentException("이미 동일한 배송지가 등록되어 있습니다.");
			}

			CompanyDeliveryAddress address = new CompanyDeliveryAddress();
			address.setCompany(company);
			address.setZipCode(zipCode);
			address.setDoName(doName);
			address.setSiName(siName);
			address.setGuName(guName);
			address.setRoadAddress(roadAddress);
			address.setDetailAddress(detailAddress);
			address.setCreatedAt(LocalDateTime.now());

			companyDeliveryAddressRepository.save(address);

			return ResponseEntity.ok(buildAdminClientDeliveryAddressResponse(
					companyId,
					"배송지가 추가되었습니다."
			));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(
					new AdminClientDeliveryAddressResponse(false, e.getMessage(), List.of())
			);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					new AdminClientDeliveryAddressResponse(
							false,
							"배송지 추가 중 오류가 발생했습니다.",
							List.of()
					)
			);
		}
	}

	@DeleteMapping("/clientDetail/{companyId}/deliveryAddresses/{addressId}")
	@ResponseBody
	public ResponseEntity<AdminClientDeliveryAddressResponse> deleteClientDeliveryAddress(
			@PathVariable Long companyId,
			@PathVariable Long addressId) {
		try {
			findAdminClientCompany(companyId);

			CompanyDeliveryAddress address = companyDeliveryAddressRepository.findById(addressId)
					.orElseThrow(() -> new IllegalArgumentException("삭제할 배송지가 존재하지 않습니다."));

			Long ownerCompanyId = address.getCompany() != null ? address.getCompany().getId() : null;
			if (!companyId.equals(ownerCompanyId)) {
				throw new IllegalArgumentException("해당 대리점의 배송지가 아니므로 삭제할 수 없습니다.");
			}

			companyDeliveryAddressRepository.delete(address);

			return ResponseEntity.ok(buildAdminClientDeliveryAddressResponse(
					companyId,
					"배송지가 삭제되었습니다."
			));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(
					new AdminClientDeliveryAddressResponse(false, e.getMessage(), List.of())
			);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					new AdminClientDeliveryAddressResponse(
							false,
							"배송지 삭제 중 오류가 발생했습니다.",
							List.of()
					)
			);
		}
	}

	private Company findAdminClientCompany(Long companyId) {
		if (companyId == null || companyId <= 0) {
			throw new IllegalArgumentException("올바르지 않은 대리점 ID입니다.");
		}

		return companyRepository.findById(companyId)
				.orElseThrow(() -> new IllegalArgumentException(
						"해당 대리점이 존재하지 않습니다. ID=" + companyId));
	}

	private AdminClientDeliveryAddressResponse buildAdminClientDeliveryAddressResponse(
			Long companyId,
			String message) {
		List<AdminClientDeliveryAddressItem> addresses =
				companyDeliveryAddressRepository.findByCompany_IdOrderByIdAsc(companyId).stream()
						.map(this::toAdminClientDeliveryAddressItem)
						.toList();

		return new AdminClientDeliveryAddressResponse(true, message, addresses);
	}

	private AdminClientDeliveryAddressItem toAdminClientDeliveryAddressItem(CompanyDeliveryAddress address) {
		return new AdminClientDeliveryAddressItem(
				address.getId(),
				adminClientDeliveryAddressText(address.getZipCode()),
				adminClientDeliveryAddressText(address.getDoName()),
				adminClientDeliveryAddressText(address.getSiName()),
				adminClientDeliveryAddressText(address.getGuName()),
				adminClientDeliveryAddressText(address.getRoadAddress()),
				adminClientDeliveryAddressText(address.getDetailAddress()),
				buildOrderListFullAddress(address)
		);
	}

	private String normalizeAdminClientDeliveryAddressValue(
			String value,
			int maxLength,
			boolean required,
			String fieldName) {
		String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");

		if (required && normalized.isBlank()) {
			throw new IllegalArgumentException(fieldName + " 항목은 필수입니다.");
		}

		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " 항목은 " + maxLength + "자 이하로 입력해주세요.");
		}

		return normalized;
	}

	private boolean isSameAdminClientDeliveryAddress(
			CompanyDeliveryAddress address,
			String zipCode,
			String roadAddress,
			String detailAddress) {
		/*
		 * 기존 데이터의 시/구 컬럼이 잘못 저장되어 있어도 같은 실주소가 중복 등록되지 않도록
		 * 우편번호 + 도로명주소 + 상세주소 기준으로 비교합니다.
		 */
		return adminClientDeliveryAddressComparisonText(address.getZipCode()).equals(
				adminClientDeliveryAddressComparisonText(zipCode))
				&& adminClientDeliveryAddressComparisonText(address.getRoadAddress()).equals(
						adminClientDeliveryAddressComparisonText(roadAddress))
				&& adminClientDeliveryAddressComparisonText(address.getDetailAddress()).equals(
						adminClientDeliveryAddressComparisonText(detailAddress));
	}

	private String adminClientDeliveryAddressComparisonText(String value) {
		return value == null
				? ""
				: value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private String adminClientDeliveryAddressText(String value) {
		return value == null ? "" : value;
	}

	@PostMapping(value = "/clientDetail/{id}/updateCompany", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseBody
	public ResponseEntity<AdminClientApiResponse> updateCompany(@PathVariable Long id,
			@ModelAttribute AdminClientCompanyUpdateRequest request) {
		try {
			adminClientDetailService.updateCompany(id, request);
			return ResponseEntity.ok(new AdminClientApiResponse(true, "회사정보가 수정되었습니다."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(new AdminClientApiResponse(false, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new AdminClientApiResponse(false, "회사정보 수정 중 오류가 발생했습니다."));
		}
	}

	@PostMapping("/member/{memberId}/updateInfo")
	@ResponseBody
	public ResponseEntity<AdminClientApiResponse> updateMemberInfo(@PathVariable Long memberId,
			@RequestBody AdminClientMemberUpdateRequest request) {
		try {
			adminClientDetailService.updateMemberInfo(memberId, request);
			return ResponseEntity.ok(new AdminClientApiResponse(true, "고객정보가 수정되었습니다."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(new AdminClientApiResponse(false, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new AdminClientApiResponse(false, "고객정보 수정 중 오류가 발생했습니다."));
		}
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

		// 전체 팀 카테고리 목록
		// HTML option에 data-team-id를 심어두고 JS에서 선택한 팀 기준으로 필터링
		List<TeamCategory> teamCategories = teamCategoryRepository.findAll();

		// 시도 정보
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

	// ===== 멤버 담당구역 일괄 저장(상하위 포함 충돌 검증 + 본인 하위구역 통합) =====
	@PostMapping("/member/regions/bulk")
	@ResponseBody
	public ResponseEntity<ApiResponse<MemberManagementService.RegionSaveResult>> saveMemberRegions(
			@RequestBody RegionBulkSaveRequest req,
			@RequestParam(value = "confirmConsolidation", defaultValue = "false") boolean confirmConsolidation) {

		MemberManagementService.RegionSaveResult result = memberMgmtService
				.saveMemberRegionsWithHierarchy(req, confirmConsolidation);

		if (!result.success()) {
			String message = result.requiresConfirmation()
					? "상위 담당구역 등록을 위해 기존 하위 담당구역 통합 확인이 필요합니다."
					: "다른 멤버의 담당 구역과 중복되어 저장되지 않았습니다.";
			return ResponseEntity.ok(ApiResponse.fail(message, result));
		}

		return ResponseEntity.ok(ApiResponse.ok(result));
	}
}
