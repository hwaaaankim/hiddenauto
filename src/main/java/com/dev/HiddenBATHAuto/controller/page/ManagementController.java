package com.dev.HiddenBATHAuto.controller.page;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/management")
public class ManagementController {

	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private OrderRepository orderRepository;
	
	@GetMapping("/admin-only")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminOnlyInManagement() {
        return "관리자만 접근 가능한 페이지입니다.";
    }

    // 관리자 또는 매니저 모두 접근 가능
    @GetMapping("/shared")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT')")
    public String adminOrManagerAccess() {
    	
        return "관리자 또는 매니저가 접근 가능한 페이지입니다.";
    }
	
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

	    if (order.getOrderItem() != null) {
	        try {
	            ObjectMapper objectMapper = new ObjectMapper();
	            Map<String, String> parsed = objectMapper.readValue(order.getOrderItem().getOptionJson(), new TypeReference<>() {});
	            model.addAttribute("optionMap", parsed); // ✅ Map<String, String>으로 바로 전달
	        } catch (Exception e) {
	            System.out.println("❌ 옵션 파싱 실패: " + e.getMessage());
	            model.addAttribute("optionMap", Map.of()); // 빈 값 전달
	        }
	    }

	    Map<String, String> imageTypeMap = Map.of(
	    	    "고객 업로드", "CUSTOMER",
	    	    "관리자 업로드", "MANAGEMENT",
	    	    "배송 완료", "DELIVERY",
	    	    "배송 증빙", "PROOF"
	    	);
    	
	    model.addAttribute("imageTypeMap", imageTypeMap);
	    model.addAttribute("order", order);
	    return "administration/order/nonStandard/orderItemDetail";
	}
	
}
