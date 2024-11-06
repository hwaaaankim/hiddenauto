package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderController {

	@GetMapping("/standardOrderProduct")
	public String standardOrderProduct() {
		
		return "front/order/standardOrderProduct";
	}
	
	@GetMapping("/nonStandardOrderProduct")
	public String nonStandardOrderProduct() {
		
		return "front/order/nonStandardOrderProduct";
	}
	
	@GetMapping("/cart")
	public String cart() {
		
		return "front/order/cart";
	}
	
	@GetMapping("/orderConfirm")
	public String orderConfirm() {
		
		return "front/order/orderConfirm";
	}
	
	@GetMapping("/modeling")
	public String modeling() {
		
		return "front/order/modeling";
	}
	
	@GetMapping("/threed")
	public String threed() {
		
		return "front/order/threed";
	}
	
	@GetMapping("/productMark")
	public String productMark() {
		
		return "front/order/productMark";
	}
}
