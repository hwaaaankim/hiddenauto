package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.as.AsTaskService;
import com.dev.HiddenBATHAuto.service.order.OrderUpdateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/management")
public class ManagementController {

	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private DeliveryMethodRepository deliveryMethodRepository;
	@Autowired
	private TeamCategoryRepository teamCategoryRepository;
	@Autowired
	private OrderUpdateService orderUpdateService;
	@Autowired
    private AsTaskService asTaskService;
	
    @GetMapping("/standardOrderList")
	public String standardOrderList() {
		
		return "administration/order/standard/orderList";
	}
	
	@GetMapping("/standardOrderDetail")
	public String standardOrderDetail() {
		
		return "administration/order/standard/orderDetail";
	}
	
	@GetMapping("/nonStandardTaskList")
	public String nonStandardTaskList(
			Model model,
			@PageableDefault(size = 10) Pageable pageable
			) {
		Page<Task> tasks = taskRepository.findAllByOrderByIdDesc(pageable);
		
		int startPage = Math.max(1, tasks.getPageable().getPageNumber() - 4);
		int endPage = Math.min(tasks.getTotalPages(), tasks.getPageable().getPageNumber() + 4);
		
		model.addAttribute("startPage", startPage);
		model.addAttribute("endPage", endPage);
		model.addAttribute("tasks", tasks);
		
		return "administration/order/nonStandard/taskList";
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
	                Map<String, String> parsed = objectMapper.readValue(
	                    item.getOptionJson(),
	                    new com.fasterxml.jackson.core.type.TypeReference<>() {}
	                );
	                item.setParsedOptionMap(parsed);
	            } catch (Exception e) {
	                System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
	            }
	        }
	    }

	    model.addAttribute("task", task);
	    return "administration/order/nonStandard/taskDetail";
	}


	@GetMapping("/nonStandardOrderItemDetail/{orderId}")
	public String nonStandardOrderItemDetail(@PathVariable Long orderId, Model model) {
	    Order order = orderRepository.findById(orderId).orElseThrow();

	    // 옵션 파싱
	    if (order.getOrderItem() != null) {
	        try {
	            ObjectMapper objectMapper = new ObjectMapper();
	            Map<String, String> parsed = objectMapper.readValue(order.getOrderItem().getOptionJson(), new TypeReference<>() {});
	            model.addAttribute("optionMap", parsed);
	        } catch (Exception e) {
	            System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
	            model.addAttribute("optionMap", Map.of());
	        }
	    }

	    // 이미지 타입 맵
	    Map<String, String> imageTypeMap = Map.of(
	        "고객 업로드", "CUSTOMER",
	        "관리자 업로드", "MANAGEMENT",
	        "배송 완료", "DELIVERY",
	        "배송 증빙", "PROOF"
	    );
	    model.addAttribute("imageTypeMap", imageTypeMap);

	    // ✅ 추가 데이터
	    model.addAttribute("order", order);
	    model.addAttribute("orderStatuses", OrderStatus.values());
	    model.addAttribute("deliveryMethods", deliveryMethodRepository.findAll());
	    model.addAttribute("deliveryTeamMembers", memberRepository.findByTeamName("배송팀"));
	    model.addAttribute("productionTeamMembers", memberRepository.findByTeamName("생산팀"));
	    model.addAttribute("productionTeamCategories",
	    	    teamCategoryRepository.findByTeamName("생산팀"));

	    return "administration/order/nonStandard/orderItemDetail";
	}
	
	@PostMapping("/nonStandardOrderItemUpdate/{orderId}")
	public String updateNonStandardOrderItem(
	        @PathVariable Long orderId,
	        @RequestParam("productCost") int productCost,
	        @RequestParam("preferredDeliveryDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate preferredDeliveryDate,
	        @RequestParam("status") String statusStr,
	        @RequestParam("deliveryMethodId") Optional<Long> deliveryMethodId,
	        @RequestParam("assignedDeliveryHandlerId") Optional<Long> deliveryHandlerId,
	        @RequestParam("productCategoryId") Optional<Long> productCategoryId) {

	    orderUpdateService.updateOrder(orderId, productCost, preferredDeliveryDate,
	                                   statusStr, deliveryMethodId, deliveryHandlerId, productCategoryId);

	    return "redirect:/management/nonStandardOrderItemDetail/" + orderId;
	}
	
	@GetMapping("/asList")
    public String asList(Model model, Pageable pageable) {
        Page<AsTask> asPage = asTaskService.getAsList(pageable);
        model.addAttribute("asPage", asPage);
        return "administration/as/asList"; // Thymeleaf 경로 예시
    }

	@GetMapping("/asDetail/{id}")
	public String asDetail(@PathVariable Long id, Model model) {
	    AsTask asTask = asTaskService.getAsDetail(id);

	    model.addAttribute("asTask", asTask);
	    model.addAttribute("asStatuses", AsStatus.values());

	    // ✅ 필요 시 서비스 통해 AS팀 멤버 조회
	    model.addAttribute("asTeamMembers", memberRepository.findByTeamName("AS팀"));

	    return "administration/as/asDetail";
	}

	@PostMapping("/asUpdate/{id}")
    public String updateAsTask(@PathVariable Long id,
                               @RequestParam(required = false) Integer price,
                               @RequestParam String status,
                               @RequestParam(required = false) Long assignedHandlerId) {

        asTaskService.updateAsTask(id, price, status, assignedHandlerId);

        return "redirect:/management/asDetail/" + id;
    }
}
