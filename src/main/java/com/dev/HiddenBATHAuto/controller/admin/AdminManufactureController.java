package com.dev.HiddenBATHAuto.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminManufactureController {

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
