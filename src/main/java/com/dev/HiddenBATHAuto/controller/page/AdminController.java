package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@GetMapping("/test")
	@ResponseBody
	public String adminTest() {
		
		return "admin";
	}
	
	@GetMapping("/index")
	public String index() {
		
		return "administration/index";
	}
	
	@GetMapping("/standardOrderList")
	public String standardOrderList() {
		
		return "administration/order/standard/orderList";
	}
	
	@GetMapping("/standardOrderDetail")
	public String standardOrderDetail() {
		
		return "administration/order/standard/orderDetail";
	}
	
	@GetMapping("/nonStandardOrderList")
	public String nonStandardOrderList() {
		
		return "administration/order/nonStandard/orderList";
	}
	
	@GetMapping("/nonStandardOrderDetail")
	public String nonStandardOrderDetail() {
		
		return "administration/order/nonStandard/orderDetail";
	}
	
	@GetMapping("/invoice")
	public String invoice() {
		
		return "administration/paper/invoice";
	}
	
	@GetMapping("/manufactureList")
	public String manufactureList() {
		return "administration/manufacture/list";
	}
	
	@GetMapping("/manufactureDetail")
	public String manufactureDetail() {
		return "administration/manufacture/detail";
	}
	
	@GetMapping("/manufacturePaper")
	public String manufacturePaper() {
		return "administration/manufacture/paper";
	}
}
