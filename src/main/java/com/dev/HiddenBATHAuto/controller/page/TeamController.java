package com.dev.HiddenBATHAuto.controller.page;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.dto.as.TeamAsDetailModalResponse;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryExcelRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryOrderSummaryRes;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryReorderByTaskRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryReorderByTaskResponse;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPageResponse;
import com.dev.HiddenBATHAuto.dto.production.ProductionListExcelRowDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionOrderCheckResponse;
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
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.order.DeliveryOrderIndexService;
import com.dev.HiddenBATHAuto.service.order.OrderService;
import com.dev.HiddenBATHAuto.service.production.MaterialCuttingService;
import com.dev.HiddenBATHAuto.service.production.ProductionListExcelService;
import com.dev.HiddenBATHAuto.service.team.TeamTaskService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryExcelService;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryOrderSummaryService;
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@PreAuthorize("hasRole('INTERNAL_EMPLOYEE')")
@RequiredArgsConstructor
public class TeamController {

	private final TeamTaskService teamTaskService;
	private final TeamCategoryRepository teamCategoryRepository;
	private final OrderRepository orderRepository;
	private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
	private final DeliveryOrderIndexService deliveryOrderIndexService;
	private final AsTaskService asTaskService;
	private final AsImageRepository asImageRepository;
	private final ProvinceRepository provinceRepository;
	private final OrderService orderService;
	private final DeliveryOrderSummaryService deliveryOrderSummaryService;
	private final DeliveryExcelService deliveryExcelService;
	private final ProductionListExcelService productionListExcelService;
	
	private final MaterialCuttingService materialCuttingService;
	private final ObjectMapper objectMapper;
	
	private static final Long AS_TEAM_ID = 4L;

	@GetMapping("/productionList")
	public String getProductionOrders(@AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) Long productCategoryId,
	        @RequestParam(required = false, defaultValue = "preferred") String dateType,
	        @RequestParam(required = false, defaultValue = "CONFIRMED") String statusFilter,

	        @RequestParam(required = false, defaultValue = "50") int size,
	        @RequestParam(required = false, defaultValue = "0") int page,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
	        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
	        @RequestParam(required = false) String sortKey,
	        @RequestParam(required = false) String sortDir,
	        Model model) {

	    Member member = principal.getMember();

	    if (member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
	    }

	    boolean isCuttingProductionMember = isCuttingProductionMember(member);
	    boolean isLowerCabinetProductionMember = isLowerCabinetProductionMember(member);

	    /*
	     * 핵심 변경점
	     * - 일반 생산팀 직원: productCategoryId 없으면 자기 팀 카테고리 기준
	     * - 재단 직원: productCategoryId 없으면 null 유지 => 전체 제품분류 조회
	     */
	    Long targetCategoryId;

	    if (productCategoryId != null) {
	        targetCategoryId = productCategoryId;
	    } else if (isCuttingProductionMember) {
	        targetCategoryId = null;
	    } else {
	        targetCategoryId = member.getTeamCategory() != null
	                ? member.getTeamCategory().getId()
	                : null;
	    }

	    String normalizedDateType = (dateType == null || dateType.isBlank())
	            ? "preferred"
	            : dateType.trim();

	    if (!"preferred".equals(normalizedDateType) && !"created".equals(normalizedDateType)) {
	        normalizedDateType = "preferred";
	    }

	    LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
	    LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

	    if (size != 10 && size != 30 && size != 50 && size != 100) {
	        size = 50;
	    }

	    if (page < 0) {
	        page = 0;
	    }

	    String sf = (statusFilter == null || statusFilter.isBlank())
	            ? "CONFIRMED"
	            : statusFilter.trim().toUpperCase();

	    OrderStatus statusEnum = null;

	    if (!"ALL".equals(sf)) {
	        try {
	            statusEnum = OrderStatus.valueOf(sf);
	        } catch (Exception e) {
	            statusEnum = OrderStatus.CONFIRMED;
	            sf = "CONFIRMED";
	        }
	    }

	    String normalizedSortKey = (sortKey == null || sortKey.isBlank())
	            ? "checked"
	            : sortKey.trim();

	    String normalizedSortDir = (sortDir == null || sortDir.isBlank())
	            ? "ASC"
	            : sortDir.trim().toUpperCase();

	    if (!"ASC".equals(normalizedSortDir) && !"DESC".equals(normalizedSortDir)) {
	        normalizedSortDir = "ASC";
	    }

	    boolean checkedSort = "checked".equalsIgnoreCase(normalizedSortKey);

	    Pageable pageable = PageRequest.of(
	            page,
	            size,
	            checkedSort
	                    ? Sort.unsorted()
	                    : buildProductionSort(normalizedSortKey, normalizedSortDir, normalizedDateType)
	    );

	    Page<Order> orderPage;

	    if (checkedSort) {
	        orderPage = teamTaskService.getProductionOrdersByDateTypeAndStatusFilterCheckedSorted(
	                targetCategoryId,
	                normalizedDateType,
	                statusEnum,
	                start,
	                end,
	                normalizedSortDir,
	                pageable
	        );
	    } else {
	        orderPage = teamTaskService.getProductionOrdersByDateTypeAndStatusFilter(
	                targetCategoryId,
	                normalizedDateType,
	                statusEnum,
	                start,
	                end,
	                pageable
	        );
	    }

	    /*
	     * 생산완료 가능 여부
	     * - 재단 직원: 무조건 불가
	     * - 하부장 등 기존 제한 대상: 자기 카테고리 조회일 때만 가능
	     * - 그 외 생산팀 직원: 가능
	     */
	    boolean canBulkComplete = true;

	    if (isCuttingProductionMember) {
	        canBulkComplete = false;
	    } else if (isLowerCabinetProductionMember) {
	        canBulkComplete = member.getTeamCategory() != null
	                && Objects.equals(member.getTeamCategory().getId(), targetCategoryId);
	    }

	    /*
	     * 자재재단 버튼은 하부장 직원에게만 노출
	     */
	    boolean canMaterialCutting = isLowerCabinetProductionMember;

	    Map<Long, String> orderCompanyNameMap = new HashMap<>();

	    for (Order o : orderPage.getContent()) {
	        String companyName = "-";

	        try {
	            if (o.getTask() != null
	                    && o.getTask().getRequestedBy() != null
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

	    Map<Long, List<ProductionOverviewFieldDto>> orderBriefFieldMap =
	            teamTaskService.buildProductionOverviewBriefFieldMap(orderPage.getContent());

	    List<Long> currentPageOrderIds = orderPage.getContent()
	            .stream()
	            .map(Order::getId)
	            .collect(Collectors.toList());

	    List<TeamCategory> productCategories = teamCategoryRepository.findByTeamName("생산팀");

	    model.addAttribute("canMaterialCutting", canMaterialCutting);
	    model.addAttribute("orders", orderPage.getContent());
	    model.addAttribute("page", orderPage);

	    model.addAttribute("productCategoryId", targetCategoryId);
	    model.addAttribute("dateType", normalizedDateType);
	    model.addAttribute("statusFilter", sf);

	    model.addAttribute("size", size);
	    model.addAttribute("startDate", startDate);
	    model.addAttribute("endDate", endDate);
	    model.addAttribute("productCategories", productCategories);

	    model.addAttribute("canBulkComplete", canBulkComplete);
	    model.addAttribute("canChangeProductionStatus", canBulkComplete);
	    model.addAttribute("isCuttingProductionMember", isCuttingProductionMember);

	    model.addAttribute("orderCompanyNameMap", orderCompanyNameMap);

	    model.addAttribute("orderBriefFieldMap", orderBriefFieldMap);
	    model.addAttribute("currentPageOrderIds", currentPageOrderIds);

	    model.addAttribute("sortKey", normalizedSortKey);
	    model.addAttribute("sortDir", normalizedSortDir);

	    return "administration/team/production/productionList";
	}
	
	private boolean isCuttingProductionMember(Member member) {
	    if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
	        return false;
	    }

	    if (member.getTeamCategory() == null) {
	        return false;
	    }

	    String categoryName = member.getTeamCategory().getName();

	    return "재단".equals(categoryName);
	}
	
	@GetMapping("/productionList/cutting")
	public String getProductionMaterialCuttingPage(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam("orderIds") List<Long> orderIds,
	        Model model
	) throws Exception {

	    if (principal == null || principal.getMember() == null) {
	        throw new AccessDeniedException("로그인이 필요합니다.");
	    }

	    Member member = principal.getMember();

	    if (!isLowerCabinetProductionMember(member)) {
	        throw new AccessDeniedException("자재재단 화면은 하부장 직원만 접근할 수 있습니다.");
	    }

	    MaterialCuttingPageResponse cuttingData =
	            materialCuttingService.buildCuttingPage(orderIds, member);

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
		
	private Sort buildProductionSort(String sortKey, String sortDir, String dateType) {
	    String key = (sortKey == null || sortKey.isBlank()) ? "date" : sortKey.trim();
	    String dir = (sortDir == null || sortDir.isBlank()) ? "DESC" : sortDir.trim().toUpperCase();

	    Sort.Direction direction = "ASC".equals(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;

	    if ("standard".equalsIgnoreCase(key)) {
	        return Sort.by(direction, "standard")
	                .and(Sort.by(Sort.Direction.DESC, "id"));
	    }

	    if ("productName".equalsIgnoreCase(key)) {
	        return Sort.by(direction, "orderItem.productName")
	                .and(Sort.by(Sort.Direction.DESC, "id"));
	    }

	    if ("date".equalsIgnoreCase(key)) {
	        String field = "created".equalsIgnoreCase(dateType)
	                ? "createdAt"
	                : "preferredDeliveryDate";

	        return Sort.by(direction, field)
	                .and(Sort.by(Sort.Direction.DESC, "id"));
	    }

	    String field = "created".equalsIgnoreCase(dateType)
	            ? "createdAt"
	            : "preferredDeliveryDate";

	    return Sort.by(Sort.Direction.DESC, field)
	            .and(Sort.by(Sort.Direction.DESC, "id"));
	}

	@GetMapping("/productionList/{orderId}/management-images")
    @ResponseBody
    public ResponseEntity<List<ProductionOverviewImageDto>> getProductionManagementImages(
            @PathVariable Long orderId,
            @AuthenticationPrincipal(expression = "member") Member loginMember
    ) {
        return ResponseEntity.ok(teamTaskService.getProductionManagementImages(orderId, loginMember));
    }

    @GetMapping("/productionList/excel")
    public void downloadProductionListExcel(
            @RequestParam("orderIds") List<Long> orderIds,
            @AuthenticationPrincipal(expression = "member") Member loginMember,
            HttpServletResponse response
    ) throws IOException {

        List<ProductionListExcelRowDto> rows =
                teamTaskService.getProductionListExcelRowsByOrderIds(orderIds, loginMember);

        String encodedFileName = URLEncoder
                .encode("생산팀_제작목록_" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFileName
        );

        try (Workbook workbook = productionListExcelService.createProductionListWorkbook(rows)) {
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        }
    }
	
	@GetMapping("/productionList/overview-data")
	@ResponseBody
	public ResponseEntity<?> getProductionOverviewData(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) String orderIds) {

	    try {
	        Member member = principal.getMember();

	        List<Long> parsedOrderIds = parseProductionOverviewOrderIds(orderIds);

	        List<ProductionOverviewOrderDto> result =
	                teamTaskService.getProductionOverviewOrders(parsedOrderIds, member);

	        return ResponseEntity.ok(result);

	    } catch (AccessDeniedException e) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN)
	                .body(Map.of("message", e.getMessage()));

	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body(Map.of("message", e.getMessage()));
	    }
	}

	private List<Long> parseProductionOverviewOrderIds(String orderIds) {
	    if (orderIds == null || orderIds.isBlank()) {
	        return List.of();
	    }

	    return Arrays.stream(orderIds.split(","))
	            .map(String::trim)
	            .filter(v -> !v.isBlank())
	            .map(Long::valueOf)
	            .filter(Objects::nonNull)
	            .distinct()
	            .collect(Collectors.toList());
	}	
	
	@PostMapping("/productionList/{orderId}/complete")
	@ResponseBody
	public ResponseEntity<?> completeProductionOrderFromOverview(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @PathVariable Long orderId) {

	    try {
	        Member member = principal.getMember();

	        if (isCuttingProductionMember(member)) {
	            throw new AccessDeniedException("재단 직원은 생산완료 처리를 할 수 없습니다.");
	        }

	        ProductionOverviewCompleteResponse response =
	                teamTaskService.completeProductionOrderFromOverview(orderId, member);

	        return ResponseEntity.ok(response);

	    } catch (AccessDeniedException e) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN)
	                .body(Map.of("message", e.getMessage()));

	    } catch (IllegalStateException e) {
	        return ResponseEntity.status(HttpStatus.CONFLICT)
	                .body(Map.of("message", e.getMessage()));

	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body(Map.of("message", e.getMessage()));
	    }
	}
	
	@PostMapping("/productionList/{orderId}/check")
	@ResponseBody
	public ResponseEntity<?> checkProductionOrder(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @PathVariable Long orderId) {

	    try {
	        Member member = principal.getMember();

	        ProductionOrderCheckResponse response =
	                teamTaskService.markProductionOrderChecked(orderId, member);

	        return ResponseEntity.ok(response);

	    } catch (AccessDeniedException e) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN)
	                .body(Map.of("message", e.getMessage()));

	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body(Map.of("message", e.getMessage()));
	    }
	}
	
	@GetMapping("/productionDetail/{orderId}")
	public String getProductionDetail(
	        @PathVariable Long orderId,
	        @AuthenticationPrincipal PrincipalDetails principal,
	        Model model) {

	    // 1. 로그인 멤버 확인
	    if (principal == null || principal.getMember() == null) {
	        throw new AccessDeniedException("로그인이 필요합니다.");
	    }

	    Member loginMember = principal.getMember();

	    // 2. 생산팀 접근 여부 확인
	    if (loginMember.getTeam() == null || !"생산팀".equals(loginMember.getTeam().getName())) {
	        throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
	    }

	    // 3. 주문 조회
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

	    // 4. 상세 진입 시 확인 처리
	    teamTaskService.markProductionOrderChecked(orderId, loginMember);

	    // 5. 주문 상품 옵션 JSON 파싱
	    OrderItem orderItem = order.getOrderItem();

	    if (orderItem != null && orderItem.getOptionJson() != null && !orderItem.getOptionJson().isBlank()) {
	        try {
	            ObjectMapper mapper = new ObjectMapper();

	            Map<String, String> parsedMap = mapper.readValue(
	                    orderItem.getOptionJson(),
	                    new TypeReference<Map<String, String>>() {}
	            );

	            orderItem.setParsedOptionMap(parsedMap);

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    // 6. 재단 직원 여부
	    boolean isCuttingProductionMember = isCuttingProductionMember(loginMember);

	    // 7. 생산완료 가능 여부
	    boolean canChangeStatus = false;

	    /*
	     * 생산완료 가능 조건
	     * 1) 재단 직원이 아니어야 함
	     * 2) 로그인 직원의 팀 카테고리와 주문 제품 카테고리가 같아야 함
	     * 3) 주문 상태가 CONFIRMED 여야 함
	     */
	    if (!isCuttingProductionMember
	            && loginMember.getTeamCategory() != null
	            && order.getProductCategory() != null
	            && loginMember.getTeamCategory().getId().equals(order.getProductCategory().getId())
	            && order.getStatus() == OrderStatus.CONFIRMED) {
	        canChangeStatus = true;
	    }

	    // 8. 모델 세팅
	    model.addAttribute("order", order);
	    model.addAttribute("orderItem", orderItem);
	    model.addAttribute("canChangeStatus", canChangeStatus);
	    model.addAttribute("isCuttingProductionMember", isCuttingProductionMember);

	    return "administration/team/production/productionDetail";
	}

	@PostMapping("/updateStatus/{orderId}")
	public String updateProductionStatus(@PathVariable Long orderId,
	                                     @RequestParam("status") OrderStatus newStatus,
	                                     @AuthenticationPrincipal PrincipalDetails principal,
	                                     RedirectAttributes redirectAttributes) {

	    // 1. 로그인한 멤버 확인
	    if (principal == null || principal.getMember() == null) {
	        redirectAttributes.addFlashAttribute("errorMessage", "로그인이 필요합니다.");
	        return "redirect:/loginForm";
	    }

	    Member loginMember = principal.getMember();

	    // 2. 오더 조회
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new IllegalArgumentException("주문이 존재하지 않습니다."));

	    // 3. 생산팀 여부 확인
	    if (loginMember.getTeam() == null || !"생산팀".equals(loginMember.getTeam().getName())) {
	        redirectAttributes.addFlashAttribute("errorMessage", "생산팀만 상태를 변경할 수 있습니다.");
	        return "redirect:/team/productionDetail/" + orderId;
	    }

	    // 4. 재단 직원은 생산완료 처리 불가
	    if (isCuttingProductionMember(loginMember)) {
	        redirectAttributes.addFlashAttribute("errorMessage", "재단 직원은 생산완료 처리를 할 수 없습니다.");
	        return "redirect:/team/productionDetail/" + orderId;
	    }

	    // 5. 상태 변경 허용 조건 확인
	    if (order.getStatus() != OrderStatus.CONFIRMED || newStatus != OrderStatus.PRODUCTION_DONE) {
	        redirectAttributes.addFlashAttribute("errorMessage", "변경 가능한 상태가 아닙니다.");
	        return "redirect:/team/productionDetail/" + orderId;
	    }

	    // 6. 권한 체크: 로그인한 멤버의 팀 카테고리와 오더의 제품 카테고리가 같아야 함
	    if (loginMember.getTeamCategory() == null || order.getProductCategory() == null) {
	        redirectAttributes.addFlashAttribute("errorMessage", "상태 변경 권한을 확인할 수 없습니다.");
	        return "redirect:/team/productionDetail/" + orderId;
	    }

	    if (!loginMember.getTeamCategory().getId().equals(order.getProductCategory().getId())) {
	        redirectAttributes.addFlashAttribute("errorMessage", "상태 변경 권한이 없습니다.");
	        return "redirect:/team/productionDetail/" + orderId;
	    }

	    // 7. 상태 변경
	    order.setStatus(OrderStatus.PRODUCTION_DONE);
	    order.setUpdatedAt(LocalDateTime.now());
	    orderRepository.save(order);

	    redirectAttributes.addFlashAttribute("successMessage", "상태가 성공적으로 변경되었습니다.");
	    return "redirect:/team/productionDetail/" + orderId;
	}

	@GetMapping("/deliveryList")
	public String getDeliveryOrders(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDate,
	        @RequestParam(required = false) OrderStatus status,
	        Model model
	) {
	    if (principal == null || principal.getMember() == null) {
	        throw new AccessDeniedException("로그인이 필요합니다.");
	    }

	    Member member = principal.getMember();

	    if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
	    }

	    if (preferredDate == null) {
	        preferredDate = LocalDate.now().plusDays(1);
	    }

	    /*
	     * 배송팀 리스트에서 허용하는 조회 상태
	     * - CONFIRMED: 승인완료, 상세확인만 가능
	     * - PRODUCTION_DONE: 생산완료, 출고 전이므로 배송완료 처리 불가
	     * - DISPATCH_DONE: 출고완료, 배송완료 처리 가능
	     * - DELIVERY_DONE: 배송완료, 상세확인만 가능
	     *
	     * CANCELED, REQUESTED 등은 배송팀 업무 상태 조회 대상에서 제외합니다.
	     */
	    List<OrderStatus> availableDeliveryStatuses = List.of(
	            OrderStatus.CONFIRMED,
	            OrderStatus.PRODUCTION_DONE,
	            OrderStatus.DISPATCH_DONE,
	            OrderStatus.DELIVERY_DONE
	    );

	    OrderStatus selectedStatus = status;

	    if (selectedStatus != null && !availableDeliveryStatuses.contains(selectedStatus)) {
	        selectedStatus = null;
	    }

	    List<OrderStatus> statuses = selectedStatus != null
	            ? List.of(selectedStatus)
	            : availableDeliveryStatuses;

	    List<DeliveryOrderIndex> all = deliveryOrderIndexRepository.findListByHandlerAndDateAndStatusIn(
	            member.getId(),
	            preferredDate,
	            statuses
	    );

	    List<DeliveryOrderIndex> pendingOrders = all.stream()
	            .filter(x -> x.getOrder() != null && x.getOrder().getStatus() != OrderStatus.DELIVERY_DONE)
	            .collect(Collectors.toList());

	    List<DeliveryOrderIndex> doneOrders = all.stream()
	            .filter(x -> x.getOrder() != null && x.getOrder().getStatus() == OrderStatus.DELIVERY_DONE)
	            .collect(Collectors.toList());

	    enrichOrderItems(pendingOrders);
	    enrichOrderItems(doneOrders);

	    model.addAttribute("deliveryHandlerId", member.getId());
	    model.addAttribute("preferredDate", preferredDate);

	    model.addAttribute("pendingOrders", pendingOrders);
	    model.addAttribute("doneOrders", doneOrders);

	    model.addAttribute("status", selectedStatus);
	    model.addAttribute("availableStatuses", availableDeliveryStatuses);

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
	    }
	}

	@PostMapping("/updateOrderIndex")
	@ResponseBody
	public ResponseEntity<?> updateOrderIndex(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestBody DeliveryOrderIndexUpdateRequest request
	) {
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
	 * ✅ 업체별정렬 API (pending DOM 순서 기반 stable grouping) - DB 저장은 '순서 저장'
	 * 버튼(updateOrderIndex)에서 수행하는 구조
	 */
	@PostMapping("/reorderByTask")
	@ResponseBody
	public ResponseEntity<?> reorderByTask(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestBody DeliveryReorderByTaskRequest request
	) {
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

	    List<Long> reordered = deliveryOrderIndexService.reorderPendingOrderIdsByTask(
	            member.getId(),
	            request.getDeliveryDate(),
	            request.getPendingOrderIds()
	    );

	    return ResponseEntity.ok(new DeliveryReorderByTaskResponse(reordered));
	}

	/**
	 * ✅ 기존 배송완료 컨트롤러: fetch(multipart)에서도 자연스럽게 동작하도록 개선 - 기존 redirect 유지 - 단,
	 * fetch 요청(X-Requested-With=fetch)인 경우 JSON 응답으로 처리
	 */
	@PostMapping("/deliveryStatus/{orderId}")
	public Object updateDeliveryStatusAndUploadImages(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @PathVariable Long orderId,
	        @RequestParam(value = "status", required = false) String status,
	        @RequestParam(value = "files", required = false) List<MultipartFile> files,
	        RedirectAttributes redirectAttributes,
	        HttpServletRequest httpServletRequest
	) {
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

	        Order order = orderRepository.findById(orderId)
	                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

	        /*
	         * 핵심 검증
	         * 배송팀은 출고완료(DISPATCH_DONE) 상태의 주문만
	         * 배송완료(DELIVERY_DONE)로 변경할 수 있습니다.
	         */
	        if (order.getStatus() != OrderStatus.DISPATCH_DONE) {
	            throw new IllegalStateException("출고완료 상태의 주문만 배송완료 처리할 수 있습니다.");
	        }

	        if (!OrderStatus.DELIVERY_DONE.name().equals(status)) {
	            throw new IllegalStateException("배송팀은 배송완료 처리만 할 수 있습니다.");
	        }

	        orderService.updateDeliveryStatusAndImages(
	                orderId,
	                OrderStatus.DELIVERY_DONE.name(),
	                files
	        );

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

	        redirectAttributes.addFlashAttribute(
	                "errorMessage",
	                "배송 상태 변경 실패: " + (e.getMessage() != null ? e.getMessage() : "알 수 없는 오류")
	        );
	    }

	    return "redirect:/team/deliveryDetail/" + orderId;
	}

	@GetMapping("/deliveryOrderSummary/{orderId}")
	@ResponseBody
	public ResponseEntity<?> getDeliveryOrderSummary(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @PathVariable Long orderId
	) {
	    if (principal == null || principal.getMember() == null) {
	        throw new AccessDeniedException("로그인이 필요합니다.");
	    }

	    Member member = principal.getMember();

	    if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
	    }

	    DeliveryOrderSummaryRes res = deliveryOrderSummaryService.getSummary(member.getId(), orderId);

	    return ResponseEntity.ok(res);
	}

	/**
	 * ✅ 엑셀 출력 (현재 DOM 순서 그대로 전송받아 A4 맞춤 XLSX 생성)
	 */
	@PostMapping("/deliveryExcel")
	public ResponseEntity<?> downloadDeliveryExcel(
	        @AuthenticationPrincipal PrincipalDetails principal,
	        @RequestBody DeliveryExcelRequest request
	) {
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

	    if (request.getOrderedOrderIds() == null || request.getOrderedOrderIds().isEmpty()) {
	        return ResponseEntity.badRequest().body("잘못된 요청입니다.(출력 대상 없음)");
	    }

	    byte[] bytes = deliveryExcelService.buildExcel(
	            member.getId(),
	            request.getDeliveryDate(),
	            request.getOrderedOrderIds()
	    );

	    String filename = "delivery_" + request.getDeliveryDate() + ".xlsx";

	    return ResponseEntity.ok()
	            .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
	            .body(bytes);
	}

	@GetMapping("/deliveryDetail/{id}")
	public String getDeliveryDetailPage(
	        @PathVariable Long id,
	        @AuthenticationPrincipal PrincipalDetails principal,
	        Model model
	) {
	    if (principal == null || principal.getMember() == null) {
	        throw new AccessDeniedException("로그인이 필요합니다.");
	    }

	    Member member = principal.getMember();

	    if (member.getTeam() == null || !"배송팀".equals(member.getTeam().getName())) {
	        throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
	    }

	    Order order = orderRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("해당 주문을 찾을 수 없습니다."));

	    OrderItem orderItem = order.getOrderItem();

	    if (orderItem != null && orderItem.getOptionJson() != null && !orderItem.getOptionJson().isBlank()) {
	        try {
	            Map<String, String> parsedMap = objectMapper.readValue(
	                    orderItem.getOptionJson(),
	                    new TypeReference<Map<String, String>>() {}
	            );

	            orderItem.setParsedOptionMap(parsedMap);

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    if (orderItem != null) {
	        OrderItemOptionJsonUtil.enrich(orderItem);
	    }

	    model.addAttribute("order", order);
	    model.addAttribute("orderItem", orderItem);

	    return "administration/team/delivery/deliveryDetail";
	}

	@GetMapping("/asList")
    public String getAsList(
            @AuthenticationPrincipal PrincipalDetails principal,

            @RequestParam(required = false, defaultValue = "requested") String dateType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @RequestParam(required = false) String status,
            @RequestParam(required = false) String companyKeyword,

            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long districtId,

            @RequestParam(required = false) String visitTimeSort,
            @RequestParam(required = false) String scheduledDateSort,
            @RequestParam(required = false) String addressSort,
            @RequestParam(required = false) String statusSort,

            Pageable pageable,
            Model model) {

        Member member = principal.getMember();

        if (member.getTeam() == null || !"AS팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("AS팀만 접근할 수 있습니다.");
        }

        // AS팀 리스트에서는 진행중/완료만 허용
        AsStatus statusEnum = parseAsTeamListStatus(status);

        LocalDateTime start = null;
        LocalDateTime end = null;

        /*
         * 중요:
         * requested / processed 는 [start, end+1day)
         * scheduled 는 AsTaskSchedule.scheduledDate(LocalDate) 기준이므로
         * endDate 그대로 넘겨서 서비스에서 LocalDate 비교(<=) 하도록 처리
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

        Page<AsTask> asPage = asTaskService.getAsTasksForAsTeamList(
                member,
                dateType,
                start,
                end,
                statusEnum,
                companyKeyword,
                provinceId,
                cityId,
                districtId,
                visitTimeSort,
                scheduledDateSort,
                normalizedAddressSort,
                normalizedStatusSort,
                pageable
        );

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
     * AS팀 리스트 전용 상태 파서
     * - 진행중 / 완료만 허용
     * - REQUESTED, CANCELED, 이상값은 전부 null 처리 => "전체(진행중+완료)"
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
	public String asDetail(@PathVariable Long id,
	                       Model model,
	                       @AuthenticationPrincipal PrincipalDetails principal) {

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
	public String updateAsTaskFromTeam(@PathVariable Long id,
	                                   @AuthenticationPrincipal PrincipalDetails principal,
	                                   @RequestParam(value = "assignedHandlerId", required = false) Long assignedHandlerId,
	                                   @RequestParam(value = "status", required = false) AsStatus status,
	                                   @RequestParam(value = "handlerMemo", required = false) String handlerMemo,
	                                   @RequestParam(value = "visitPlannedDate", required = false)
	                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitPlannedDate,
	                                   @RequestParam(value = "visitPlannedTime", required = false)
	                                   @DateTimeFormat(pattern = "HH:mm") LocalTime visitPlannedTime,
	                                   @RequestParam(value = "resultImages", required = false) List<MultipartFile> resultImages,
	                                   RedirectAttributes redirectAttributes) {

	    Member loginMember = principal != null ? principal.getMember() : null;
	    validateAsTeamMember(loginMember);

	    try {
	        boolean handlerChanged = asTaskService.updateAsTaskByHandler(
	                id,
	                loginMember,
	                assignedHandlerId,
	                status,
	                handlerMemo,
	                visitPlannedDate,
	                visitPlannedTime,
	                resultImages
	        );

	        if (handlerChanged) {
	            redirectAttributes.addFlashAttribute(
	                    "success",
	                    "담당자가 변경되어 기존 방문 일정이 삭제되었습니다. 현재 계정에서는 더 이상 해당 AS 상세를 조회할 수 없습니다."
	            );
	            return "redirect:/team/asList";
	        }

	        redirectAttributes.addFlashAttribute(
	                "success",
	                "AS 상태, 담당자, 담당자용 메모, 방문예정일, 방문예정시간, 결과 이미지가 저장되었습니다."
	        );
	        return "redirect:/team/asDetail/" + id;

	    } catch (Exception e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute(
	                "error",
	                e.getMessage() != null ? e.getMessage() : "저장 중 오류가 발생했습니다."
	        );
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

		boolean isSubLeaderTeam = (member.getTeamCategory() != null
				&& "하부장".equals(member.getTeamCategory().getName()));
		Long allowedCategoryId = isSubLeaderTeam ? member.getTeamCategory().getId() : null;

		List<StickerPrintDto> items = teamTaskService.getStickerPrintItems(orderIds, allowedCategoryId);

		List<List<StickerPrintDto>> pages = new ArrayList<>();
		for (int i = 0; i < items.size(); i += 4) {
			pages.add(items.subList(i, Math.min(i + 4, items.size())));
		}

		model.addAttribute("pages", pages);
		model.addAttribute("totalCount", items.size());
		model.addAttribute("today", LocalDate.now()); // ✅ 추가

		return "administration/team/production/productionStickerPrint";
	}
}
