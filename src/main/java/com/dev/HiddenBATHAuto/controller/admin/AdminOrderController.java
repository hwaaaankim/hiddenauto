package com.dev.HiddenBATHAuto.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminOrderController {
	
	@GetMapping("/normalOrderList")
	public String normalOrderList() {
		
		return "administration/order/normalOrderList";
	}
	
	@GetMapping("/normalOrderDetail")
	public String normalOrderDetail() {
		
		return "administration/order/normalOrderDetail";
	}
	
	@GetMapping("/normalOrderInsert")
	public String normalOrderInsert() {
		
		return "administration/order/normalOrderInsert";
	}
}
