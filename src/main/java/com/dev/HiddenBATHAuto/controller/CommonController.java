package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CommonController {

	@GetMapping("/search")
	public String search() {
		
		return "front/common/search";
	}
	
	@GetMapping("/orderProduct")
	public String orderProduct() {
		
		return "front/common/orderProduct";
	}

}
