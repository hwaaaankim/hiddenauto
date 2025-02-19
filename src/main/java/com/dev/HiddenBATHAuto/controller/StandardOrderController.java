package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StandardOrderController {

	@GetMapping("/standardOrderProduct")
	public String standardOrderProduct() {

		return "front/order/standardOrderProduct";
	}
}
