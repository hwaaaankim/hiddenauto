package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.ProvinceRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.auth.CompanyService;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.order.OrderStatusService;
import com.dev.HiddenBATHAuto.service.order.OrderUpdateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	
	@GetMapping("/standardOrderList")
	public String standardOrderList() {

		return "administration/order/standard/orderList";
	}

	@GetMapping("/standardOrderDetail")
	public String standardOrderDetail() {

		return "administration/order/standard/orderDetail";
	}

	@GetMapping("/nonStandardTaskList")
	public String nonStandardTaskList(Model model, @PageableDefault(size = 10) Pageable pageable) {
		Page<Task> tasks = taskRepository.findAllByOrderByIdDesc(pageable);

		int startPage = Math.max(1, tasks.getPageable().getPageNumber() - 4);
		int endPage = Math.min(tasks.getTotalPages(), tasks.getPageable().getPageNumber() + 4);

		model.addAttribute("startPage", startPage);
		model.addAttribute("endPage", endPage);
		model.addAttribute("tasks", tasks);

		return "administration/management/order/nonStandard/taskList";
	}

	@GetMapping("/nonStandardTaskDetail/{id}")
	public String nonStandardTaskDetail(@PathVariable Long id, Model model) {
		Task task = taskRepository.findById(id).orElseThrow();

		// 이미 저장된 optionJson은 한글이므로 그대로 파싱해서 사용
		for (Order order : task.getOrders()) {
			OrderItem item = order.getOrderItem();
			if (item != null) {
				try {
					ObjectMapper objectMapper = new ObjectMapper();
					Map<String, String> parsed = objectMapper.readValue(item.getOptionJson(),
							new com.fasterxml.jackson.core.type.TypeReference<>() {
							});
					item.setParsedOptionMap(parsed);
				} catch (Exception e) {
					System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
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

		return "administration/management/order/nonStandard/orderItemDetail";
	}

	@PostMapping("/nonStandardOrderItemUpdate/{orderId}")
	public String updateNonStandardOrderItem(@PathVariable Long orderId, @RequestParam("productCost") int productCost,
			@RequestParam("preferredDeliveryDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDeliveryDate,
			@RequestParam("status") String statusStr, @RequestParam("deliveryMethodId") Optional<Long> deliveryMethodId,
			@RequestParam("assignedDeliveryHandlerId") Optional<Long> deliveryHandlerId,
			@RequestParam("productCategoryId") Optional<Long> productCategoryId) {

		orderUpdateService.updateOrder(orderId, productCost, preferredDeliveryDate, statusStr, deliveryMethodId,
				deliveryHandlerId, productCategoryId);

		return "redirect:/management/nonStandardOrderItemDetail/" + orderId;
	}

	@GetMapping("/asList")
	public String asList(@AuthenticationPrincipal PrincipalDetails principal,
			@RequestParam(required = false) Long handlerId, @RequestParam(required = false) AsStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Pageable pageable, Model model) {
		LocalDate targetDate = (date != null) ? date : LocalDate.now();
		Long memberId = handlerId != null ? handlerId : null;

		Page<AsTask> asPage = asTaskService.getFilteredAsList(memberId, status, targetDate, pageable);
		model.addAttribute("asPage", asPage);
		model.addAttribute("asHandlers", memberRepository.findByTeamName("AS팀"));
		model.addAttribute("selectedDate", targetDate);
		model.addAttribute("selectedStatuses", status);
		model.addAttribute("selectedHandlerId", handlerId);

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

	@PostMapping("/asUpdate/{id}")
	public String updateAsTask(@PathVariable Long id, @RequestParam(required = false) Integer price,
			@RequestParam String status, @RequestParam(required = false) Long assignedHandlerId) {

		asTaskService.updateAsTask(id, price, status, assignedHandlerId);

		return "redirect:/management/asDetail/" + id;
	}

	@GetMapping("/productionList")
	public String productionListPage(@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Pageable pageable, Model model) {

		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;
		LocalDate targetDate = (date != null) ? date : LocalDate.now().plusDays(1);

		OrderStatus parsedStatus;
		if (status == null) {
			parsedStatus = OrderStatus.CONFIRMED;
			status = OrderStatus.CONFIRMED.name(); // 🔧 문자열로 다시 설정 (화면 select 유지)
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

		Page<Order> orders = orderStatusService.getOrders(targetDate, category, parsedStatus, pageable);

		model.addAttribute("orders", orders);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("status", status); // 문자열 그대로 전달
		model.addAttribute("date", targetDate);
		model.addAttribute("categories", teamCategoryRepository.findByTeamName("생산팀"));
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/production/productionList";
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
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Pageable pageable, Model model) {

		TeamCategory category = (categoryId != null) ? teamCategoryRepository.findById(categoryId).orElse(null) : null;
		LocalDate targetDate = (date != null) ? date : LocalDate.now().plusDays(1);

		OrderStatus parsedStatus;
		if (status == null) {
			parsedStatus = OrderStatus.PRODUCTION_DONE;
			status = OrderStatus.PRODUCTION_DONE.name(); // 🔧 문자열로 다시 설정 (화면 select 유지)
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

		Page<Order> orders = orderStatusService.getOrders(targetDate, category, parsedStatus, pageable);

		model.addAttribute("orders", orders);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("status", status); // 문자열 그대로 전달
		model.addAttribute("date", targetDate);
		model.addAttribute("categories", teamCategoryRepository.findByTeamName("배송팀"));
		model.addAttribute("orderStatusList", OrderStatus.values());

		return "administration/management/delivery/deliveryList";
	}

	@GetMapping("/clientList")
	public String clientList(@RequestParam(required = false) String keyword,
	                         @RequestParam(required = false, defaultValue = "company") String searchType,
	                         @PageableDefault(size=10) Pageable pageable,
	                         Model model) {

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
	public String employeeList(
	        @RequestParam(value = "name", required = false) String name,
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
	public String employeeInsert(
			@ModelAttribute MemberSaveDTO request
			) {
		
		
		memberService.saveMember(request);
		return "redirect:/management/employeeInsertForm";
	}

	@PostMapping("/employeeUpdate")
	@ResponseBody
	public String employeeUpdate() {

		return "success";
	}
}
